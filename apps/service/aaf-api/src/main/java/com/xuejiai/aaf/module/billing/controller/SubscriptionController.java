package com.xuejiai.aaf.module.billing.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.billing.domain.EntitlementDef;
import com.xuejiai.aaf.module.billing.domain.SubscriptionPlan;
import com.xuejiai.aaf.module.billing.repository.EntitlementDefRepository;
import com.xuejiai.aaf.module.billing.repository.PlanEntitlementRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.service.SubscriptionService;
import com.xuejiai.aaf.module.billing.vo.SubscribeDTO;
import com.xuejiai.aaf.module.billing.vo.SubscriptionPlanVO;
import com.xuejiai.aaf.module.billing.vo.SubscriptionVO;

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

    /** 购买/升级订阅 */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/subscribe")
    public Result<Long> subscribe(
            @RequestParam(required = false) Long userId, @Valid @RequestBody SubscribeDTO dto) {
        var recordId =
                subscriptionService.subscribe(
                        ownerId(userId), dto.planCode(), dto.channelCode(), dto.isYearly());
        return Result.success(recordId);
    }

    /** 获取当前订阅 */
    @GetMapping("/current")
    public Result<SubscriptionVO> current(@RequestParam(required = false) Long userId) {
        var sub = subscriptionService.getActiveSubscription(ownerId(userId));
        if (sub == null) {
            return Result.success(null);
        }
        var plan = planRepository.findById(sub.getPlanId()).orElse(null);
        return Result.success(
                new SubscriptionVO(
                        sub.getId(),
                        plan != null ? plan.getCode() : null,
                        plan != null ? plan.getName() : null,
                        sub.getStartAt(),
                        sub.getEndAt(),
                        sub.getStatus()));
    }

    private Long ownerId(Long fallbackUserId) {
        return operatorContext.currentOwnerId().orElse(fallbackUserId);
    }
}
