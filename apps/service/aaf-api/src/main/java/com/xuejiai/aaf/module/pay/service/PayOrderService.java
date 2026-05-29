package com.xuejiai.aaf.module.pay.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.pay.PayOrderStatusEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.settlement.*;
import com.xuejiai.aaf.module.pay.domain.PayOrder;
import com.xuejiai.aaf.module.pay.repository.PayOrderRepository;
import com.xuejiai.aaf.module.pay.vo.PayNotifyDTO;
import com.xuejiai.aaf.module.pay.vo.PayOrderCreateDTO;
import com.xuejiai.aaf.module.pay.vo.PayOrderVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 支付订单服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayOrderService {

    private final PayOrderRepository payOrderRepository;
    private final SettlementEngine settlementEngine;

    /** 创建支付单并发起支付 */
    @Transactional
    public PayOrderVO create(PayOrderCreateDTO dto) {
        var order = new PayOrder();
        order.setMerchantOrderNo(dto.merchantOrderNo());
        order.setSubject(dto.subject());
        order.setBody(dto.body());
        order.setAmount(dto.amount());
        order.setChannelCode(dto.channelCode());
        order.setUserId(dto.userId());
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));
        payOrderRepository.save(order);

        // 调用结算引擎发起支付
        var result =
                settlementEngine.charge(
                        new ChargeRequest(
                                dto.merchantOrderNo(),
                                dto.amount(),
                                dto.subject(),
                                dto.channelCode(),
                                null));

        if (result.success()) {
            // 模拟支付等同步成功场景：直接标记支付成功
            order.setStatus(PayOrderStatusEnum.SUCCESS.getCode());
            order.setChannelOrderNo(result.channelOrderNo());
            order.setSuccessTime(LocalDateTime.now());
        }
        payOrderRepository.save(order);
        return toVO(order);
    }

    /** 支付回调处理，返回支付单 ID（供上层触发业务逻辑） */
    @Transactional
    public Long handleNotify(PayNotifyDTO dto) {
        var order =
                payOrderRepository
                        .findByMerchantOrderNo(dto.merchantOrderNo())
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "支付单不存在"));

        if (!order.getStatus().equals(PayOrderStatusEnum.WAITING.getCode())) {
            log.warn("支付单已处理，忽略回调: merchantOrderNo={}", dto.merchantOrderNo());
            return null;
        }

        if (dto.success()) {
            order.setStatus(PayOrderStatusEnum.SUCCESS.getCode());
            order.setChannelOrderNo(dto.channelOrderNo());
            order.setSuccessTime(LocalDateTime.now());
        } else {
            order.setStatus(PayOrderStatusEnum.CLOSED.getCode());
        }
        payOrderRepository.save(order);
        log.info("支付回调处理完成: merchantOrderNo={}, success={}", dto.merchantOrderNo(), dto.success());
        return dto.success() ? order.getId() : null;
    }

    /** 判断支付单是否已成功 */
    public boolean isSuccess(Long payOrderId) {
        return payOrderRepository.findById(payOrderId)
                .map(o -> o.getStatus().equals(PayOrderStatusEnum.SUCCESS.getCode()))
                .orElse(false);
    }

    /** 查询支付单 */
    @Transactional(readOnly = true)
    public PayOrderVO getById(Long id) {
        var order =
                payOrderRepository
                        .findById(id)
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "支付单不存在"));
        return toVO(order);
    }

    private PayOrderVO toVO(PayOrder o) {
        return new PayOrderVO(
                o.getId(),
                o.getMerchantOrderNo(),
                o.getSubject(),
                o.getAmount(),
                o.getStatus(),
                o.getChannelCode(),
                o.getChannelOrderNo(),
                o.getUserId(),
                o.getExpireTime(),
                o.getSuccessTime(),
                o.getRefundAmount(),
                o.getCreateTime());
    }
}
