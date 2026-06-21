package com.xuejiai.aaf.module.billing.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.billing.SubscriptionOperationEnum;
import com.xuejiai.aaf.common.enums.billing.SubscriptionStatusEnum;
import com.xuejiai.aaf.common.enums.pay.BizOrderTypeEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.module.billing.domain.Subscription;
import com.xuejiai.aaf.module.billing.domain.SubscriptionRecord;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRecordRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.module.pay.handler.PaySuccessHandler;
import com.xuejiai.aaf.module.pay.service.BizOrderService;
import com.xuejiai.aaf.module.pay.service.PayOrderService;
import com.xuejiai.aaf.module.pay.vo.BizOrderCreateDTO;
import com.xuejiai.aaf.module.pay.vo.PayOrderCreateDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 订阅服务（付费线）。
 *
 * <p>购买订阅复用 BizOrderService/PayOrderService 发起支付， 支付成功后创建 subscription + 实例化 entitlement_quota。
 */
@Slf4j
@Service("billingSubscriptionService")
@RequiredArgsConstructor
public class SubscriptionService implements PaySuccessHandler {

    @Override
    public String bizOrderType() {
        return BizOrderTypeEnum.SUBSCRIPTION.getCode();
    }

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionRecordRepository recordRepository;
    private final SubscriptionPlanRepository planRepository;
    private final BizOrderService bizOrderService;
    private final PayOrderService payOrderService;
    private final EntitlementService entitlementService;
    private final CreditService creditService;

    @org.springframework.context.annotation.Lazy
    private final com.xuejiai.aaf.module.brokerage.service.BrokerageService brokerageService;

    private final com.xuejiai.aaf.module.system.user.repository.UserRepository userRepository;

    /** 购买订阅：创建业务订单 + 支付单 */
    @Transactional
    public Long subscribe(Long userId, String planCode, String channelCode) {
        return subscribe(userId, planCode, channelCode, false);
    }

    /** 购买订阅：创建业务订单 + 支付单（支持年付） */
    @Transactional
    public Long subscribe(Long userId, String planCode, String channelCode, boolean yearly) {
        var plan =
                planRepository
                        .findByCode(planCode)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "套餐不存在: " + planCode));

        // 免费套餐直接激活
        if (plan.getPrice() == 0) {
            return activateSubscription(userId, plan.getId(), null, false);
        }

        // 年付价格 = 月付 * 12 * 0.8
        long actualPrice = yearly ? Math.round(plan.getPrice() * 12 * 0.8) : plan.getPrice();
        String planLabel = plan.getName() + (yearly ? "（年付）" : "（月付）");

        // 创建业务订单
        var bizOrder =
                bizOrderService.create(
                        userId,
                        new BizOrderCreateDTO(
                                BizOrderTypeEnum.SUBSCRIPTION.getCode(),
                                "订阅 " + planLabel,
                                actualPrice,
                                channelCode));

        // 创建支付单
        var payOrder =
                payOrderService.create(
                        new PayOrderCreateDTO(
                                bizOrder.orderNo(),
                                "订阅 " + planLabel,
                                null,
                                actualPrice,
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
        record.setPayPrice(actualPrice);
        record.setPayStatus("UNPAID");
        record.setYearly(yearly);
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
        if (bizOrder == null
                || !BizOrderTypeEnum.SUBSCRIPTION.getCode().equals(bizOrder.getOrderType())) {
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
            activateSubscription(
                    userId,
                    record.getPlanId(),
                    record.getId(),
                    Boolean.TRUE.equals(record.getYearly()));
        }
    }

    /** 到期处理：将过期订阅标记为 EXPIRED */
    @Transactional
    public int expireSubscriptions() {
        var now = LocalDateTime.now();
        var expiredSubscriptions =
                subscriptionRepository.findByStatusAndEndAtBefore(
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
        return subscriptionRepository
                .findByUserIdAndStatus(userId, SubscriptionStatusEnum.ACTIVE.getCode())
                .orElse(null);
    }

    // ===== 私有方法 =====

    public Long activateSubscription(Long userId, Long planId, Long sourceId, boolean yearly) {
        var plan =
                planRepository
                        .findById(planId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "套餐不存在"));

        // 取消旧订阅
        subscriptionRepository
                .findByUserIdAndStatus(userId, SubscriptionStatusEnum.ACTIVE.getCode())
                .ifPresent(
                        old -> {
                            old.setStatus(SubscriptionStatusEnum.CANCELLED.getCode());
                            subscriptionRepository.save(old);
                        });

        // 年付有效期 365 天，月付按套餐 durationDays
        int durationDays = yearly ? 365 : plan.getDurationDays();

        // 创建新订阅
        var subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setPlanId(planId);
        subscription.setStartAt(LocalDateTime.now());
        subscription.setEndAt(durationDays > 0 ? LocalDateTime.now().plusDays(durationDays) : null);
        subscription.setStatus(SubscriptionStatusEnum.ACTIVE.getCode());
        subscription.setSourceId(sourceId);
        subscriptionRepository.save(subscription);

        // 实例化权益额度
        entitlementService.instantiateQuotas(userId, planId);

        // 发放首月积分（套餐配置了 monthly_credits 时）
        if (plan.getMonthlyCredits() > 0) {
            creditService.earnBatch(
                    userId,
                    plan.getMonthlyCredits(),
                    "SUBSCRIPTION",
                    "SUBSCRIPTION_ACTIVATE",
                    String.valueOf(subscription.getId()),
                    LocalDateTime.now().plusDays(30));
            subscription.setLastCreditIssuedAt(LocalDateTime.now());
            subscriptionRepository.save(subscription);
        }

        log.info(
                "订阅激活: userId={}, plan={}, endAt={}",
                userId,
                plan.getCode(),
                subscription.getEndAt());

        // 付费套餐激活后尝试自动开通分销资格（免费套餐 FREE 不触发）
        if (plan.getPrice() > 0) {
            try {
                userRepository
                        .findById(userId)
                        .ifPresent(
                                user -> {
                                    if (user.getContactId() != null) {
                                        brokerageService.tryEnableBrokerage(
                                                user.getContactId(), "PAID");
                                    }
                                });
            } catch (Exception e) {
                log.warn("分销资格自动开通失败，不影响订阅流程: userId={}", userId, e);
            }
        }

        return subscription.getId();
    }
}
