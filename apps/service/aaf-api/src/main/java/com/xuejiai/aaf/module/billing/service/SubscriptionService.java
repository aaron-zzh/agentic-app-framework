package com.xuejiai.aaf.module.billing.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.billing.SubscriptionOperationEnum;
import com.xuejiai.aaf.common.enums.billing.SubscriptionStatusEnum;
import com.xuejiai.aaf.common.enums.pay.BizOrderTypeEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.module.billing.domain.Subscription;
import com.xuejiai.aaf.module.billing.domain.SubscriptionPlan;
import com.xuejiai.aaf.module.billing.domain.SubscriptionRecord;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRecordRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.module.pay.handler.PaySuccessHandler;
import com.xuejiai.aaf.module.pay.service.BizOrderService;
import com.xuejiai.aaf.module.pay.service.PayOrderService;
import com.xuejiai.aaf.module.pay.vo.BizOrderCreateDTO;
import com.xuejiai.aaf.module.pay.vo.PayOrderCreateDTO;
import com.xuejiai.aaf.module.pay.vo.PayOrderVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 订阅服务（付费线）。
 *
 * <p>购买订阅复用 BizOrderService/PayOrderService 发起支付， 支付成功后创建 subscription + 实例化 entitlement_quota。
 *
 * <p>AAF-099 v0.2.0 补全：
 *
 * <ul>
 *   <li>{@link #cancel(Long)}：取消订阅（仅记 cancelled_at + auto_renew=false，权益保留至 end_at）
 *   <li>{@link #downgrade(Long, String, boolean)}：降级排队（end_at 切换，不付钱）
 *   <li>{@link #cancelPending(Long)}：撤销已申请的降级
 *   <li>{@link #upgrade(Long, String, String, boolean)}：升级订阅（按时间比例补差价 + 三笔积分流水）
 * </ul>
 */
@Slf4j
@Service("billingSubscriptionService")
@RequiredArgsConstructor
public class SubscriptionService implements PaySuccessHandler {

    /** 年付折扣（与 SubscriptionController.toVO 一致：月价 * 12 * 0.8）。 */
    private static final double YEARLY_DISCOUNT = 0.8;

    /** 年付天数。 */
    private static final int YEARLY_DAYS = 365;

    /** 月度积分批次默认有效期（天）。 */
    private static final int MONTHLY_CREDIT_EXPIRE_DAYS = 30;

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
    public PayOrderVO subscribe(Long userId, String planCode, String channelCode) {
        return subscribe(userId, planCode, channelCode, false);
    }

    /** 购买订阅：创建业务订单 + 支付单（支持年付） */
    @Transactional
    public PayOrderVO subscribe(Long userId, String planCode, String channelCode, boolean yearly) {
        var plan =
                planRepository
                        .findByCode(planCode)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "套餐不存在: " + planCode));

        // 免费套餐直接激活，返回 null 表示无需支付
        if (plan.getPrice() == 0) {
            activateSubscription(userId, plan.getId(), null, false);
            return null;
        }

        // 路由：若有生效订阅，按升级/降级/续费三向分支
        var existing =
                subscriptionRepository
                        .findByUserIdAndStatus(userId, SubscriptionStatusEnum.ACTIVE.getCode())
                        .orElse(null);
        if (existing != null) {
            var oldPlan = planRepository.findById(existing.getPlanId()).orElse(null);
            boolean oldYearly = isCurrentSubYearly(existing);
            if (oldPlan != null) {
                if (isUpgrade(oldPlan, oldYearly, plan, yearly)) {
                    return upgrade(userId, planCode, channelCode, yearly);
                }
                if (isDowngrade(oldPlan, oldYearly, plan, yearly)) {
                    throw new BusinessException(
                            GlobalErrorCode.BAD_REQUEST,
                            "降级请使用 /api/billing/subscription/downgrade 接口");
                }
                // 同价位（续费同档）走原有付款流程
            }
        }

        // 年付价格 = 月付 * 12 * 0.8
        long actualPrice =
                yearly ? Math.round(plan.getPrice() * 12 * YEARLY_DISCOUNT) : plan.getPrice();
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

        return payOrder;
    }

    /**
     * 取消订阅：仅设置 cancelled_at + auto_renew=false。
     *
     * <p>当前周期权益保留至 end_at；不退款；不清除 pending_plan_id。订阅 status 仍为 ACTIVE。
     *
     * <p>幂等：已 cancelled 时直接返回。
     */
    @Transactional
    public Subscription cancel(Long userId) {
        var sub =
                subscriptionRepository
                        .findByUserIdAndStatus(userId, SubscriptionStatusEnum.ACTIVE.getCode())
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "无生效订阅"));
        if (sub.getCancelledAt() != null) {
            return sub;
        }
        sub.setCancelledAt(LocalDateTime.now());
        sub.setAutoRenew(false);
        log.info("订阅取消: userId={}, subId={}, endAt={}", userId, sub.getId(), sub.getEndAt());
        return subscriptionRepository.save(sub);
    }

    /**
     * 降级排队：在当前周期 end_at 到期时切换到目标套餐。
     *
     * <p>降级请求不付钱、不发积分、不动权益。仅记录 pending_plan_id + pending_yearly， 由 SubscriptionExpireScheduler 在
     * end_at 时激活。
     *
     * <p>校验：必须是降级（newPriceUnit &lt; oldPriceUnit）；同档年付→月付也属降级。
     */
    @Transactional
    public Subscription downgrade(Long userId, String newPlanCode, boolean newYearly) {
        var sub =
                subscriptionRepository
                        .findByUserIdAndStatus(userId, SubscriptionStatusEnum.ACTIVE.getCode())
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "无生效订阅"));
        var newPlan =
                planRepository
                        .findByCode(newPlanCode)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND,
                                                "套餐不存在: " + newPlanCode));
        var oldPlan =
                planRepository
                        .findById(sub.getPlanId())
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "当前套餐不存在"));
        boolean oldYearly = isCurrentSubYearly(sub);

        if (!isDowngrade(oldPlan, oldYearly, newPlan, newYearly)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "请使用升级接口或正常订阅接口");
        }

        sub.setPendingPlanId(newPlan.getId());
        sub.setPendingYearly(newYearly);
        log.info(
                "订阅降级排队: userId={}, oldPlanId={}, pendingPlanId={}, pendingYearly={},"
                        + " effectAt={}",
                userId,
                oldPlan.getId(),
                newPlan.getId(),
                newYearly,
                sub.getEndAt());
        return subscriptionRepository.save(sub);
    }

    /** 撤销降级：清除 pending_plan_id + pending_yearly。 */
    @Transactional
    public Subscription cancelPending(Long userId) {
        var sub =
                subscriptionRepository
                        .findByUserIdAndStatus(userId, SubscriptionStatusEnum.ACTIVE.getCode())
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "无生效订阅"));
        sub.setPendingPlanId(null);
        sub.setPendingYearly(false);
        log.info("订阅降级撤销: userId={}, subId={}", userId, sub.getId());
        return subscriptionRepository.save(sub);
    }

    /**
     * 升级订阅：按时间比例补差价，立即生效，三笔积分流水（EXPIRE/EARN/SPEND）。
     *
     * <p>差价公式：{@code payable = max(0, newPrice - oldPrice * remainingDays / totalDays)}。
     */
    @Transactional
    public PayOrderVO upgrade(
            Long userId, String newPlanCode, String channelCode, boolean newYearly) {
        var oldSub =
                subscriptionRepository
                        .findByUserIdAndStatus(userId, SubscriptionStatusEnum.ACTIVE.getCode())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND,
                                                "无生效订阅，请使用 subscribe 接口"));
        var oldPlan =
                planRepository
                        .findById(oldSub.getPlanId())
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "当前套餐不存在"));
        var newPlan =
                planRepository
                        .findByCode(newPlanCode)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND,
                                                "套餐不存在: " + newPlanCode));
        boolean oldYearly = isCurrentSubYearly(oldSub);

        if (!isUpgrade(oldPlan, oldYearly, newPlan, newYearly)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "请使用降级接口或正常订阅接口");
        }

        long newPriceUnit = computePriceUnit(newPlan, newYearly);
        long payable =
                computeUpgradePayable(
                        oldSub, oldPlan, oldYearly, newPriceUnit, LocalDateTime.now());
        String planLabel =
                "由 "
                        + oldPlan.getName()
                        + " 升级至 "
                        + newPlan.getName()
                        + (newYearly ? "（年付）" : "（月付）");

        var record = new SubscriptionRecord();
        record.setUserId(userId);
        record.setPlanId(newPlan.getId());
        record.setOperation(SubscriptionOperationEnum.UPGRADE.getCode());
        record.setPayPrice(payable);
        record.setYearly(newYearly);

        if (payable == 0) {
            // 无需付款：直接激活（仍写流水用于审计）
            record.setPayStatus("PAID");
            record.setPayTime(LocalDateTime.now());
            recordRepository.save(record);
            activateUpgrade(userId, newPlan, record.getId(), newYearly);
            return null;
        }

        // 创建业务订单 + 支付单
        var bizOrder =
                bizOrderService.create(
                        userId,
                        new BizOrderCreateDTO(
                                BizOrderTypeEnum.SUBSCRIPTION.getCode(),
                                "订阅升级 " + planLabel,
                                payable,
                                channelCode));
        var payOrder =
                payOrderService.create(
                        new PayOrderCreateDTO(
                                bizOrder.orderNo(),
                                "订阅升级 " + planLabel,
                                null,
                                payable,
                                channelCode,
                                userId));
        bizOrderService.bindPayOrder(bizOrder.id(), payOrder.id());
        record.setPayOrderId(payOrder.id());
        record.setPayStatus("UNPAID");
        recordRepository.save(record);

        // MOCK 渠道同步成功时直接激活
        if (payOrderService.isSuccess(payOrder.id())) {
            onPaySuccess(payOrder.id());
        }
        return payOrder;
    }

    /** 支付成功回调：激活订阅 + 实例化权益额度（按 operation 区分新购/升级） */
    @Override
    @Transactional
    public void onPaySuccess(Long payOrderId) {
        var bizOrder = bizOrderService.findByPayOrderId(payOrderId);
        if (bizOrder == null
                || !BizOrderTypeEnum.SUBSCRIPTION.getCode().equals(bizOrder.getOrderType())) {
            return;
        }
        bizOrderService.markPaid(bizOrder.getId());

        var userId = bizOrder.getUserId();
        var records = recordRepository.findByPayOrderIdAndPayStatus(payOrderId, "UNPAID");
        for (var record : records) {
            record.setPayStatus("PAID");
            record.setPayTime(LocalDateTime.now());
            recordRepository.save(record);
            if (SubscriptionOperationEnum.UPGRADE.getCode().equals(record.getOperation())) {
                var newPlan =
                        planRepository
                                .findById(record.getPlanId())
                                .orElseThrow(
                                        () ->
                                                new BusinessException(
                                                        GlobalErrorCode.NOT_FOUND, "套餐不存在"));
                activateUpgrade(
                        userId, newPlan, record.getId(), Boolean.TRUE.equals(record.getYearly()));
            } else {
                activateSubscription(
                        userId,
                        record.getPlanId(),
                        record.getId(),
                        Boolean.TRUE.equals(record.getYearly()));
            }
        }
    }

    /** 到期处理：将过期订阅标记为 EXPIRED（保留为兜底；主流程由 SubscriptionExpireScheduler 处理 pending 切换/冻结）。 */
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

    // ===== 公共辅助方法（供 Scheduler 调用） =====

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
        int durationDays = yearly ? YEARLY_DAYS : plan.getDurationDays();

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
                    LocalDateTime.now().plusDays(MONTHLY_CREDIT_EXPIRE_DAYS));
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
            tryEnableBrokerage(userId, plan, yearly, subscription.getId());
        }

        return subscription.getId();
    }

    /** 升级激活：取消旧订阅 + 创建新订阅 + 实例化权益 + 三笔积分流水（不重复发首月积分）。 */
    public Long activateUpgrade(
            Long userId, SubscriptionPlan newPlan, Long sourceId, boolean yearly) {
        var oldSub =
                subscriptionRepository
                        .findByUserIdAndStatus(userId, SubscriptionStatusEnum.ACTIVE.getCode())
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "无生效订阅"));

        var now = LocalDateTime.now();
        int durationDays = yearly ? YEARLY_DAYS : newPlan.getDurationDays();
        var newSub = new Subscription();
        newSub.setUserId(userId);
        newSub.setPlanId(newPlan.getId());
        newSub.setStartAt(now);
        newSub.setEndAt(durationDays > 0 ? now.plusDays(durationDays) : null);
        newSub.setStatus(SubscriptionStatusEnum.ACTIVE.getCode());
        newSub.setSourceId(sourceId);
        subscriptionRepository.save(newSub);

        // 旧订阅作废（cancelled_at 复用为升级时间标记）
        oldSub.setStatus(SubscriptionStatusEnum.CANCELLED.getCode());
        oldSub.setCancelledAt(now);
        subscriptionRepository.save(oldSub);

        // 实例化新套餐权益（覆盖式重置）
        entitlementService.instantiateQuotas(userId, newPlan.getId());

        // 三笔积分流水：EXPIRE 旧批次 / EARN 新批次 / SPEND 继承已用
        if (newPlan.getMonthlyCredits() != null && newPlan.getMonthlyCredits() > 0) {
            creditService.settleSubscriptionUpgrade(
                    userId,
                    newPlan.getMonthlyCredits(),
                    newSub.getId(),
                    now.plusDays(MONTHLY_CREDIT_EXPIRE_DAYS));
            newSub.setLastCreditIssuedAt(now);
            subscriptionRepository.save(newSub);
        }

        log.info(
                "订阅升级激活: userId={}, oldSubId={}, newPlan={}, endAt={}",
                userId,
                oldSub.getId(),
                newPlan.getCode(),
                newSub.getEndAt());

        // 分销佣金（与 activateSubscription 一致）
        if (newPlan.getPrice() > 0) {
            tryEnableBrokerage(userId, newPlan, yearly, newSub.getId());
        }
        return newSub.getId();
    }

    // ===== 私有辅助方法 =====

    private boolean isUpgrade(
            SubscriptionPlan oldPlan,
            boolean oldYearly,
            SubscriptionPlan newPlan,
            boolean newYearly) {
        return computePriceUnit(newPlan, newYearly) > computePriceUnit(oldPlan, oldYearly);
    }

    private boolean isDowngrade(
            SubscriptionPlan oldPlan,
            boolean oldYearly,
            SubscriptionPlan newPlan,
            boolean newYearly) {
        return computePriceUnit(newPlan, newYearly) < computePriceUnit(oldPlan, oldYearly);
    }

    /** 套餐实付价（年付应用 0.8 折扣）。 */
    private long computePriceUnit(SubscriptionPlan plan, boolean yearly) {
        return yearly ? Math.round(plan.getPrice() * 12 * YEARLY_DISCOUNT) : plan.getPrice();
    }

    /**
     * 升级实付差价（按时间比例计算）。
     *
     * <p>{@code oldRemainValue = oldPriceUnit * remainingDays / totalDays}；{@code payable = max(0,
     * newPrice - oldRemainValue)}。
     */
    private long computeUpgradePayable(
            Subscription oldSub,
            SubscriptionPlan oldPlan,
            boolean oldYearly,
            long newPriceUnit,
            LocalDateTime now) {
        long oldPriceUnit = computePriceUnit(oldPlan, oldYearly);
        if (oldSub.getStartAt() == null || oldSub.getEndAt() == null) {
            // 永久套餐或老数据：不做时间比例，按全价支付
            return Math.max(0L, newPriceUnit - oldPriceUnit);
        }
        long totalDays = ChronoUnit.DAYS.between(oldSub.getStartAt(), oldSub.getEndAt());
        long remainingDays = Math.max(0L, ChronoUnit.DAYS.between(now, oldSub.getEndAt()));
        long oldRemainValue =
                totalDays > 0 ? Math.round((double) oldPriceUnit * remainingDays / totalDays) : 0L;
        return Math.max(0L, newPriceUnit - oldRemainValue);
    }

    private boolean isCurrentSubYearly(Subscription sub) {
        if (sub.getSourceId() == null) return false;
        return recordRepository
                .findById(sub.getSourceId())
                .map(r -> Boolean.TRUE.equals(r.getYearly()))
                .orElse(false);
    }

    /** 付费套餐激活后尝试开通分销 + 计算佣金（与 activateSubscription 复用）。 */
    private void tryEnableBrokerage(
            Long userId, SubscriptionPlan plan, boolean yearly, Long subscriptionId) {
        try {
            userRepository
                    .findById(userId)
                    .ifPresent(
                            user -> {
                                if (user.getContactId() != null) {
                                    brokerageService.tryEnableBrokerage(
                                            user.getContactId(), "PAID");
                                    long paidAmount =
                                            yearly
                                                    ? Math.round(
                                                            plan.getPrice() * 12 * YEARLY_DISCOUNT)
                                                    : plan.getPrice();
                                    brokerageService.calculateBrokerage(
                                            user.getContactId(),
                                            "SUBSCRIBE",
                                            "PLAN",
                                            String.valueOf(plan.getId()),
                                            String.valueOf(subscriptionId),
                                            "订阅 " + plan.getName(),
                                            paidAmount);
                                }
                            });
        } catch (Exception e) {
            log.warn("分销资格自动开通失败，不影响订阅流程: userId={}", userId, e);
        }
    }
}
