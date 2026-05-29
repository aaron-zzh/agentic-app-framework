package com.xuejiai.aaf.module.billing.controller;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.billing.domain.SubscriptionPlan;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.service.SubscriptionService;
import com.xuejiai.aaf.module.billing.vo.SubscribeDTO;
import com.xuejiai.aaf.module.billing.vo.SubscriptionVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.List;

/** 订阅管理接口 */
@RestController
@RequestMapping("/api/billing/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionPlanRepository planRepository;

    /** 获取可用套餐列表 */
    @GetMapping("/plans")
    public Result<List<SubscriptionPlan>> listPlans() {
        return Result.success(planRepository.findByStatusOrderBySortAsc("ENABLED"));
    }

    /** 购买/升级订阅 */
    @PostMapping("/subscribe")
    public Result<Long> subscribe(@RequestParam Long userId, @Valid @RequestBody SubscribeDTO dto) {
        var recordId = subscriptionService.subscribe(userId, dto.planCode(), dto.channelCode());
        return Result.success(recordId);
    }

    /** 获取当前订阅 */
    @GetMapping("/current")
    public Result<SubscriptionVO> current(@RequestParam Long userId) {
        var sub = subscriptionService.getActiveSubscription(userId);
        if (sub == null) {
            return Result.success(null);
        }
        var plan = planRepository.findById(sub.getPlanId()).orElse(null);
        return Result.success(new SubscriptionVO(
                sub.getId(),
                plan != null ? plan.getCode() : null,
                plan != null ? plan.getName() : null,
                sub.getStartAt(),
                sub.getEndAt(),
                sub.getStatus()));
    }
}
