package com.xuejiai.aaf.module.pay.service;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.pay.PayOrderStatusEnum;
import com.xuejiai.aaf.module.pay.repository.PayOrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 支付订单过期关闭定时任务——将超过 expireTime 仍未支付的订单状态改为 CLOSED */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayOrderExpireTask {

    private final PayOrderRepository payOrderRepository;

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
    }
}
