package com.xuejiai.aaf.module.pay.service;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.pay.PayOrderStatusEnum;
import com.xuejiai.aaf.framework.engine.settlement.SettlementEngine;
import com.xuejiai.aaf.module.pay.repository.PayOrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 支付订单过期关闭定时任务——将超过 expireTime 仍未支付的订单状态改为 CLOSED */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayOrderExpireTask {

    private final PayOrderRepository payOrderRepository;
    private final SettlementEngine settlementEngine;

    /** 每分钟执行一次 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void expireOrders() {
        var expired =
                payOrderRepository.findByStatusAndExpireTimeBefore(
                        PayOrderStatusEnum.WAITING.getCode(), LocalDateTime.now());
        if (expired.isEmpty()) return;

        expired.forEach(o -> o.setStatus(PayOrderStatusEnum.CLOSED.getCode()));
        payOrderRepository.saveAll(expired);
        log.info("[PayOrderExpireTask] 关闭过期支付订单 {} 笔", expired.size());

        // 本地状态已落库后再通知渠道侧关闭交易，避免渠道调用异常阻塞批量状态更新；
        // 渠道侧关闭失败已在适配器内部吞掉异常并记录日志，不影响本地订单已关闭的最终结果
        expired.forEach(
                o -> {
                    try {
                        settlementEngine.close(o.getChannelCode(), o.getMerchantOrderNo());
                    } catch (Exception e) {
                        log.warn(
                                "[PayOrderExpireTask] 通知渠道关闭交易失败: merchantOrderNo={}, error={}",
                                o.getMerchantOrderNo(),
                                e.getMessage());
                    }
                });
    }
}
