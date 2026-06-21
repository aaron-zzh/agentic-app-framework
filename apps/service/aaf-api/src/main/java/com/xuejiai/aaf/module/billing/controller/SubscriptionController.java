package com.xuejiai.aaf.module.billing.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.billing.domain.EntitlementDef;
import com.xuejiai.aaf.module.billing.domain.Subscription;
import com.xuejiai.aaf.module.billing.domain.SubscriptionPlan;
import com.xuejiai.aaf.module.billing.repository.EntitlementDefRepository;
import com.xuejiai.aaf.module.billing.repository.PlanEntitlementRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.service.SubscriptionService;
import com.xuejiai.aaf.module.billing.vo.DowngradeDTO;
import com.xuejiai.aaf.module.billing.vo.SubscribeDTO;
import com.xuejiai.aaf.module.billing.vo.SubscriptionPlanVO;
import com.xuejiai.aaf.module.billing.vo.SubscriptionVO;
import com.xuejiai.aaf.module.pay.vo.PayOrderVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 订阅管理接口 */
@RestController("billingSubscriptionController")
@RequestMapping("/api/billing/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionPlanRepository planRepository;
    private final PlanEntitlementRepository planEntitlementRepository;
    private final EntitlementDefRepository entitlementDefRepository;
    private final OperatorContext operatorContext;

    /** 获取可用套餐列表 */
    @GetMapping("/plans")
    public Result<List<SubscriptionPlanVO>> listPlans() {
        var plans = planRepository.findByStatusOrderBySortAsc("ENABLED");
        var vos = plans.stream().map(this::toVO).toList();
        return Result.success(vos);
    }

    private SubscriptionPlanVO toVO(SubscriptionPlan plan) {
        // 年付价格 = 月付 * 12 * 0.8（八折）
        long yearlyPrice = Math.round(plan.getPrice() * 12 * 0.8);
        // 查权益列表
        var ents = planEntitlementRepository.findByPlanId(plan.getId());
        var entVOs =
                ents.stream()
                        .map(
                                e -> {
                                    EntitlementDef def =
                                            entitlementDefRepository
                                                    .findById(e.getEntId())
                                                    .orElse(null);
                                    return new SubscriptionPlanVO.PlanEntitlementVO(
                                            def != null ? def.getCode() : "",
                                            def != null ? def.getName() : "",
                                            def != null ? def.getType() : "",
                                            def != null ? def.getUnit() : "",
                                            e.getQuota(),
                                            e.getResetCycle(),
                                            e.getRefillPrice());
                                })
                        .toList();
        return new SubscriptionPlanVO(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDurationDays(),
                plan.getPrice(),
                yearlyPrice,
                plan.getMarketPrice(),
                plan.getMonthlyCredits(),
                plan.getExt(),
                entVOs);
    }

    /**
     * 购买/升级订阅。
     *
     * <p>路由逻辑（在 {@link SubscriptionService#subscribe} 内实现）：
     *
     * <ul>
     *   <li>无生效订阅 → 新购
     *   <li>同价位 → 续费
     *   <li>升级 → 自动转 {@link SubscriptionService#upgrade} 流程
     *   <li>降级 → 拒绝（提示使用 /downgrade 接口）
     * </ul>
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/subscribe")
    public Result<PayOrderVO> subscribe(
            @RequestParam(required = false) Long userId, @Valid @RequestBody SubscribeDTO dto) {
        var payOrder =
                subscriptionService.subscribe(
                        ownerId(userId), dto.planCode(), dto.channelCode(), dto.isYearly());
        return Result.success(payOrder);
    }

    /**
     * 取消订阅：仅记 cancelled_at + auto_renew=false。
     *
     * <p>当前周期权益保留至 end_at；不退款；不清除 pending_plan_id。订阅 status 仍为 ACTIVE 直到自然到期。
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/cancel")
    public Result<SubscriptionVO> cancel(@RequestParam(required = false) Long userId) {
        var sub = subscriptionService.cancel(ownerId(userId));
        return Result.success(toSubscriptionVO(sub));
    }

    /** 降级排队：在当前周期 end_at 到期时切换到目标套餐。降级请求不付钱、不发积分、不动权益。 */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/downgrade")
    public Result<SubscriptionVO> downgrade(
            @RequestParam(required = false) Long userId, @Valid @RequestBody DowngradeDTO dto) {
        var sub = subscriptionService.downgrade(ownerId(userId), dto.planCode(), dto.isYearly());
        return Result.success(toSubscriptionVO(sub));
    }

    /** 撤销已申请的降级：清除 pending_plan_id + pending_yearly。 */
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/pending")
    public Result<SubscriptionVO> cancelPending(@RequestParam(required = false) Long userId) {
        var sub = subscriptionService.cancelPending(ownerId(userId));
        return Result.success(toSubscriptionVO(sub));
    }

    /** 获取当前订阅 */
    @GetMapping("/current")
    public Result<SubscriptionVO> current(@RequestParam(required = false) Long userId) {
        var sub = subscriptionService.getActiveSubscription(ownerId(userId));
        if (sub == null) {
            return Result.success(null);
        }
        return Result.success(toSubscriptionVO(sub));
    }

    /** 转换 Subscription → VO，包含 plan/pendingPlan 关联信息。 */
    private SubscriptionVO toSubscriptionVO(Subscription sub) {
        var plan = planRepository.findById(sub.getPlanId()).orElse(null);
        String pendingPlanCode = null;
        if (sub.getPendingPlanId() != null) {
            pendingPlanCode =
                    planRepository
                            .findById(sub.getPendingPlanId())
                            .map(SubscriptionPlan::getCode)
                            .orElse(null);
        }
        return new SubscriptionVO(
                sub.getId(),
                plan != null ? plan.getCode() : null,
                plan != null ? plan.getName() : null,
                sub.getStartAt(),
                sub.getEndAt(),
                sub.getStatus(),
                sub.getAutoRenew(),
                sub.getCancelledAt(),
                pendingPlanCode,
                sub.getPendingYearly(),
                sub.getLastReminderAt());
    }

    private Long ownerId(Long fallbackUserId) {
        return operatorContext.currentOwnerId().orElse(fallbackUserId);
    }
}
