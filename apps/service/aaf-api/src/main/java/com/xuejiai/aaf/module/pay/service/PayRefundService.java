package com.xuejiai.aaf.module.pay.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.pay.PayRefundStatusEnum;
import com.xuejiai.aaf.framework.engine.settlement.RefundRequest;
import com.xuejiai.aaf.framework.engine.settlement.SettlementEngine;
import com.xuejiai.aaf.module.pay.ErrorCodeConstants;
import com.xuejiai.aaf.module.pay.domain.RefundOrder;
import com.xuejiai.aaf.module.pay.repository.PayOrderRepository;
import com.xuejiai.aaf.module.pay.repository.RefundOrderRepository;
import com.xuejiai.aaf.module.pay.vo.RefundApplyDTO;
import com.xuejiai.aaf.module.pay.vo.RefundOrderVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 退款服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayRefundService {

    private final RefundOrderRepository refundOrderRepository;
    private final PayOrderRepository payOrderRepository;
    private final SettlementEngine settlementEngine;

    /**
     * 申请退款（全额或部分）。
     *
     * <p>M26 并发与幂等：
     *
     * <ul>
     *   <li>幂等：客户端携带 {@code requestNo} 时先查已有退款单并直接返回，超时重试不会生成新 refundNo 重复向渠道提交（数据库 {@code
     *       uk_refund_order_request_no} 兜底）
     *   <li>并发：可退额度用 {@code reserveRefundAmount} 原子占额（申请阶段先占、渠道明确失败再释放），
     *       替代原"读-判-写"，避免并发申请累计退款超过订单金额
     * </ul>
     */
    @Transactional
    public RefundOrderVO applyRefund(RefundApplyDTO dto) {
        // M26：幂等键命中即复用，禁止重复退款
        if (dto.requestNo() != null && !dto.requestNo().isBlank()) {
            var existing = refundOrderRepository.findByRequestNo(dto.requestNo()).orElse(null);
            if (existing != null) {
                log.info(
                        "退款幂等命中，复用已有退款单: requestNo={}, refundNo={}",
                        dto.requestNo(),
                        existing.getRefundNo());
                return toVO(existing);
            }
        }

        var payOrder =
                payOrderRepository
                        .findById(dto.payOrderId())
                        .orElseThrow(() -> exception(ErrorCodeConstants.PAY_ORDER_NOT_FOUND));

        // M26：原子占用可退额度，占额失败即超过可退上限（含并发场景）
        int reserved =
                payOrderRepository.reserveRefundAmount(
                        payOrder.getId(), dto.amount(), LocalDateTime.now());
        if (reserved == 0) {
            throw exception(ErrorCodeConstants.REFUND_AMOUNT_EXCEEDED);
        }

        // 创建退款单
        var refundOrder = new RefundOrder();
        var refundNo = "RF" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        refundOrder.setRefundNo(refundNo);
        refundOrder.setRequestNo(dto.requestNo());
        refundOrder.setPayOrderId(payOrder.getId());
        refundOrder.setMerchantOrderNo(payOrder.getMerchantOrderNo());
        refundOrder.setChannelCode(payOrder.getChannelCode());
        refundOrder.setRefundAmount(dto.amount());
        refundOrder.setReason(dto.reason());
        refundOrderRepository.save(refundOrder);

        // 调用结算引擎退款（M25：原单总额随请求下发，渠道按 total/refund 正确计算部分退款）
        var result =
                settlementEngine.refund(
                        new RefundRequest(
                                payOrder.getMerchantOrderNo(),
                                refundNo,
                                dto.amount(),
                                payOrder.getAmount(),
                                dto.reason(),
                                payOrder.getChannelCode()));

        if (result.success()) {
            refundOrder.setStatus(PayRefundStatusEnum.SUCCESS.getCode());
            refundOrder.setSuccessTime(LocalDateTime.now());
            // M26：已退金额在占额阶段累加过，此处不再重复累加
        } else {
            // 非同步成功，保持 WAITING 状态等待回调；占额继续保留，避免额度被并发申请挪用
            log.info("退款已提交，等待渠道回调: refundNo={}, message={}", refundNo, result.message());
        }
        refundOrderRepository.save(refundOrder);
        return toVO(refundOrder);
    }

    /** 退款回调处理 */
    @Transactional
    public void handleRefundNotify(String refundNo, boolean success) {
        var refundOrder =
                refundOrderRepository
                        .findByRefundNo(refundNo)
                        .orElseThrow(() -> exception(ErrorCodeConstants.REFUND_ORDER_NOT_FOUND));

        if (!refundOrder.getStatus().equals(PayRefundStatusEnum.WAITING.getCode())) {
            log.warn("退款单已处理，忽略回调: refundNo={}", refundNo);
            return;
        }

        if (success) {
            refundOrder.setStatus(PayRefundStatusEnum.SUCCESS.getCode());
            refundOrder.setSuccessTime(LocalDateTime.now());
            // M26：申请阶段已原子占额，回调成功不再累加，避免同一笔退款被计两次
        } else {
            refundOrder.setStatus(PayRefundStatusEnum.FAILURE.getCode());
            // M26：渠道明确失败，释放申请阶段占用的可退额度
            payOrderRepository.releaseRefundAmount(
                    refundOrder.getPayOrderId(),
                    refundOrder.getRefundAmount(),
                    LocalDateTime.now());
        }
        refundOrderRepository.save(refundOrder);
        log.info("退款回调处理完成: refundNo={}, success={}", refundNo, success);
    }

    /** 重试失败的退款（定时任务调用）——M26：复用原 refundNo 与原占额，不新建退款单、不重复占额。 */
    @Transactional
    public void retryFailedRefunds() {
        var waitingRefunds =
                refundOrderRepository.findByStatus(PayRefundStatusEnum.WAITING.getCode());
        for (var refund : waitingRefunds) {
            var payOrder = payOrderRepository.findById(refund.getPayOrderId()).orElse(null);
            if (payOrder == null) {
                log.warn("退款重试跳过：支付单不存在, refundNo={}", refund.getRefundNo());
                continue;
            }
            var result =
                    settlementEngine.refund(
                            new RefundRequest(
                                    refund.getMerchantOrderNo(),
                                    refund.getRefundNo(),
                                    refund.getRefundAmount(),
                                    payOrder.getAmount(),
                                    refund.getReason(),
                                    refund.getChannelCode()));
            if (result.success()) {
                refund.setStatus(PayRefundStatusEnum.SUCCESS.getCode());
                refund.setSuccessTime(LocalDateTime.now());
                refundOrderRepository.save(refund);
            }
        }
    }

    /** 查询退款单 */
    @Transactional(readOnly = true)
    public RefundOrderVO getByRefundNo(String refundNo) {
        var refund =
                refundOrderRepository
                        .findByRefundNo(refundNo)
                        .orElseThrow(() -> exception(ErrorCodeConstants.REFUND_ORDER_NOT_FOUND));
        return toVO(refund);
    }

    private RefundOrderVO toVO(RefundOrder o) {
        return new RefundOrderVO(
                o.getId(),
                o.getRefundNo(),
                o.getPayOrderId(),
                o.getMerchantOrderNo(),
                o.getChannelCode(),
                o.getRefundAmount(),
                o.getStatus(),
                o.getReason(),
                o.getChannelRefundNo(),
                o.getSuccessTime(),
                o.getCreateTime());
    }
}
