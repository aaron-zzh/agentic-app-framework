package com.xuejiai.aaf.module.billing.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.billing.SubscriptionOperationEnum;
import com.xuejiai.aaf.common.enums.billing.SubscriptionStatusEnum;
import com.xuejiai.aaf.common.enums.pay.BizOrderTypeEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.billing.domain.Subscription;
import com.xuejiai.aaf.module.billing.domain.SubscriptionRecord;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRecordRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.module.pay.service.BizOrderService;
import com.xuejiai.aaf.module.pay.service.PayOrderService;
import com.xuejiai.aaf.module.pay.vo.BizOrderCreateDTO;
import com.xuejiai.aaf.module.pay.vo.PayOrderCreateDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 订阅服务（付费线）。
 *
 * <p>购买订阅复用 BizOrderService/PayOrderService 发起支付，
 * 支付成功后创建 subscription + 实例化 entitlement_quota。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionRecordRepository recordRepository;
    private final SubscriptionPlanRepository planRepository;
    private final BizOrderService bizOrderService;
    private final PayOrderService payOrderService;
    private final EntitlementService entitlementService;

    /** 购买订阅：创建业务订单 + 支付单 */
    @Transactional
    public Long subscribe(Long userId, String planCode, String channelCode) {
        var plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "套餐不存在: " + planCode));

        // 免费套餐直接激活
        if (plan.getPrice() == 0) {
            return activateSubscription(userId, plan.getId(), null);
        }

        // 创建业务订单
        var bizOrder = bizOrderService.create(userId, new BizOrderCreateDTO(
                BizOrderTypeEnum.SUBSCRIPTION.getCode(),
                "订阅 " + plan.getName(),
                plan.getPrice(),
                channelCode));

        // 创建支付单
        var payOrder = payOrderService.create(new PayOrderCreateDTO(
                bizOrder.orderNo(),
                "订阅 " + plan.getName(),
                null,
                plan.getPrice(),
                channelCode,
                userId));

        // 关联支付单
        bizOrderService.bindPayOrder(bizOrder.id(), payOrder.id());

        // 创建订阅流水（待支付）
        var record = new SubscriptionRecord();
        record.setUserId(userId);
        record.setPlanId(plan.getId());
        record.setOperation(SubscriptionOperationEnum.NEW.getCode());
        record.setPayOrderId(payOrder.id());
        record.setPayPrice(plan.getPrice());
        record.setPayStatus("UNPAID");
        recordRepository.save(record);

        // MOCK 渠道同步成功时直接激活
        if (payOrderService.isSuccess(payOrder.id())) {
            onPaySuccess(payOrder.id());
        }

        return record.getId();
    }

    /** 支付成功回调：激活订阅 + 实例化权益额度 */
    @Transactional
    public void onPaySuccess(Long payOrderId) {
        var bizOrder = bizOrderService.findByPayOrderId(payOrderId);
        if (bizOrder == null || !BizOrderTypeEnum.SUBSCRIPTION.getCode().equals(bizOrder.getOrderType())) {
            return;
        }
        bizOrderService.markPaid(bizOrder.getId());

        // 查找对应的订阅流水
        var userId = bizOrder.getUserId();
        var records = recordRepository.findByPayOrderIdAndPayStatus(payOrderId, "UNPAID");
        for (var record : records) {
            record.setPayStatus("PAID");
            record.setPayTime(LocalDateTime.now());
            recordRepository.save(record);
            activateSubscription(userId, record.getPlanId(), record.getId());
        }
    }

    /** 到期处理：将过期订阅标记为 EXPIRED */
    @Transactional
    public int expireSubscriptions() {
        var now = LocalDateTime.now();
        var expiredSubscriptions = subscriptionRepository.findByStatusAndEndAtBefore(
                SubscriptionStatusEnum.ACTIVE.getCode(), now);
        for (var sub : expiredSubscriptions) {
            sub.setStatus(SubscriptionStatusEnum.EXPIRED.getCode());
            subscriptionRepository.save(sub);
            log.info("订阅过期: userId={}, planId={}", sub.getUserId(), sub.getPlanId());
        }
        return expiredSubscriptions.size();
    }

    /** 获取用户当前有效订阅 */
    @Transactional(readOnly = true)
    public Subscription getActiveSubscription(Long userId) {
        return subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatusEnum.ACTIVE.getCode())
                .orElse(null);
    }

    // ===== 私有方法 =====

    private Long activateSubscription(Long userId, Long planId, Long sourceId) {
        var plan = planRepository.findById(planId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "套餐不存在"));

        // 取消旧订阅
        subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatusEnum.ACTIVE.getCode())
                .ifPresent(old -> {
                    old.setStatus(SubscriptionStatusEnum.CANCELLED.getCode());
                    subscriptionRepository.save(old);
                });

        // 创建新订阅
        var subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setPlanId(planId);
        subscription.setStartAt(LocalDateTime.now());
        subscription.setEndAt(plan.getDurationDays() > 0
                ? LocalDateTime.now().plusDays(plan.getDurationDays())
                : null);
        subscription.setStatus(SubscriptionStatusEnum.ACTIVE.getCode());
        subscription.setSourceId(sourceId);
        subscriptionRepository.save(subscription);

        // 实例化权益额度
        entitlementService.instantiateQuotas(userId, planId);

        log.info("订阅激活: userId={}, plan={}, endAt={}", userId, plan.getCode(), subscription.getEndAt());
        return subscription.getId();
    }
}
