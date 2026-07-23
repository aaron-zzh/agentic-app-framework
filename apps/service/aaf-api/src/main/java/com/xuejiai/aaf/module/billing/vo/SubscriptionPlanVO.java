package com.xuejiai.aaf.module.billing.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 订阅套餐展示 VO（含客户目录权益和管理元数据）。 */
public record SubscriptionPlanVO(
        Long id,
        String code,
        String name,
        Integer durationDays,
        Long price,
        Long yearlyPrice,
        Long marketPrice,
        Long monthlyCredits,
        String ext,
        List<PlanEntitlementVO> entitlements,
        String status,
        Integer sort,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public record PlanEntitlementVO(
            String code,
            String name,
            String type,
            String unit,
            Long quota,
            String resetCycle,
            Long refillPrice) {}
}
