package com.xuejiai.aaf.module.pay.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.pay.PayOrderStatusEnum;
import com.xuejiai.aaf.framework.engine.settlement.PayStatus;
import com.xuejiai.aaf.framework.engine.settlement.SettlementEngine;
import com.xuejiai.aaf.module.pay.domain.PayOrder;
import com.xuejiai.aaf.module.pay.repository.PayOrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 支付订单同步任务——定时轮询未收到回调的待支付订单 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayOrderSyncTask {

    private final PayOrderRepository payOrderRepository;
    private final SettlementEngine settlementEngine;
    private final PayNotifyService payNotifyService;

    /** 每 30 秒轮询一次未完成的支付订单 */
    @Scheduled(fixedDelay = 30_000, initialDelay = 60_000)
    public void syncPendingOrders() {
        var cutoff = LocalDateTime.now().minusMinutes(30);
        List<PayOrder> pendingOrders =
                payOrderRepository.findByStatusAndCreateTimeAfter(
                        PayOrderStatusEnum.WAITING.getCode(), cutoff);

        for (var order : pendingOrders) {
            try {
                syncOrder(order);
            } catch (Exception e) {
                log.warn("同步支付订单失败: id={}, error={}", order.getId(), e.getMessage());
            }
        }
        if (!pendingOrders.isEmpty()) {
            log.info("支付订单同步完成，处理 {} 笔", pendingOrders.size());
        }
    }

    private void syncOrder(PayOrder order) {
        var result =
                settlementEngine.queryStatus(order.getChannelCode(), order.getMerchantOrderNo());
        if (result == null) return;
        var status = result.status();
        if (status == PayStatus.PAID) {
            order.setStatus(PayOrderStatusEnum.SUCCESS.getCode());
            order.setSuccessTime(LocalDateTime.now());
            if (result.channelOrderNo() != null) {
                order.setChannelOrderNo(result.channelOrderNo());
            }
            payOrderRepository.save(order);
            payNotifyService.onPaySuccess(order.getId()); // 统一路由到对应 handler
            log.info("轮询发现支付成功: merchantOrderNo={}", order.getMerchantOrderNo());
        } else if (status == PayStatus.CLOSED) {
            order.setStatus(PayOrderStatusEnum.CLOSED.getCode());
            payOrderRepository.save(order);
            log.info("轮询发现支付关闭: merchantOrderNo={}", order.getMerchantOrderNo());
        } else if (status == PayStatus.NOT_FOUND
                && order.getCreateTime().isBefore(LocalDateTime.now().minusMinutes(2))) {
            // 下单 2 分钟后渠道侧仍查无此交易，基本可判定为下单失败的死单，无需等到 30 分钟过期
            order.setStatus(PayOrderStatusEnum.CLOSED.getCode());
            payOrderRepository.save(order);
            log.info("轮询发现交易不存在，提前关闭死单: merchantOrderNo={}", order.getMerchantOrderNo());
        }
    }
}
