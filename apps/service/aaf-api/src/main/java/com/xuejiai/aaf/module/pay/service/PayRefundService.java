package com.xuejiai.aaf.module.pay.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.pay.PayRefundStatusEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.settlement.RefundRequest;
import com.xuejiai.aaf.framework.engine.settlement.SettlementEngine;
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

    /** 申请退款（全额或部分） */
    @Transactional
    public RefundOrderVO applyRefund(RefundApplyDTO dto) {
        var payOrder =
                payOrderRepository
                        .findById(dto.payOrderId())
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "支付单不存在"));

        // 校验可退金额
        long refundable = payOrder.getAmount() - payOrder.getRefundAmount();
        if (dto.amount() > refundable) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "退款金额超过可退金额");
        }

        // 创建退款单
        var refundOrder = new RefundOrder();
        var refundNo = "RF" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        refundOrder.setRefundNo(refundNo);
        refundOrder.setPayOrderId(payOrder.getId());
        refundOrder.setMerchantOrderNo(payOrder.getMerchantOrderNo());
        refundOrder.setChannelCode(payOrder.getChannelCode());
        refundOrder.setRefundAmount(dto.amount());
        refundOrder.setReason(dto.reason());
        refundOrderRepository.save(refundOrder);

        // 调用结算引擎退款
        var result =
                settlementEngine.refund(
                        new RefundRequest(
                                payOrder.getMerchantOrderNo(),
                                refundNo,
                                dto.amount(),
                                dto.reason(),
                                payOrder.getChannelCode()));

        if (result.success()) {
            refundOrder.setStatus(PayRefundStatusEnum.SUCCESS.getCode());
            refundOrder.setSuccessTime(LocalDateTime.now());
            // 更新支付单已退金额
            payOrder.setRefundAmount(payOrder.getRefundAmount() + dto.amount());
            payOrderRepository.save(payOrder);
        } else {
            // 非同步成功，保持 WAITING 状态等待回调
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
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "退款单不存在"));

        if (!refundOrder.getStatus().equals(PayRefundStatusEnum.WAITING.getCode())) {
            log.warn("退款单已处理，忽略回调: refundNo={}", refundNo);
            return;
        }

        if (success) {
            refundOrder.setStatus(PayRefundStatusEnum.SUCCESS.getCode());
            refundOrder.setSuccessTime(LocalDateTime.now());
            // 更新支付单已退金额
            var payOrder = payOrderRepository.findById(refundOrder.getPayOrderId()).orElse(null);
            if (payOrder != null) {
                payOrder.setRefundAmount(payOrder.getRefundAmount() + refundOrder.getRefundAmount());
                payOrderRepository.save(payOrder);
            }
        } else {
            refundOrder.setStatus(PayRefundStatusEnum.FAILURE.getCode());
        }
        refundOrderRepository.save(refundOrder);
        log.info("退款回调处理完成: refundNo={}, success={}", refundNo, success);
    }

    /** 重试失败的退款（定时任务调用） */
    @Transactional
    public void retryFailedRefunds() {
        var waitingRefunds =
                refundOrderRepository.findByStatus(PayRefundStatusEnum.WAITING.getCode());
        for (var refund : waitingRefunds) {
            var result =
                    settlementEngine.refund(
                            new RefundRequest(
                                    refund.getMerchantOrderNo(),
                                    refund.getRefundNo(),
                                    refund.getRefundAmount(),
                                    refund.getReason(),
                                    refund.getChannelCode()));
            if (result.success()) {
                refund.setStatus(PayRefundStatusEnum.SUCCESS.getCode());
                refund.setSuccessTime(LocalDateTime.now());
                var payOrder = payOrderRepository.findById(refund.getPayOrderId()).orElse(null);
                if (payOrder != null) {
                    payOrder.setRefundAmount(payOrder.getRefundAmount() + refund.getRefundAmount());
                    payOrderRepository.save(payOrder);
                }
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
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "退款单不存在"));
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
