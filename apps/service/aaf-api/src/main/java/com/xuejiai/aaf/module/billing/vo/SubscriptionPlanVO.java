package com.xuejiai.aaf.module.billing.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 订阅套餐展示 VO；Plan 只表达档位，SKU 表达周期和价格。 */
public record SubscriptionPlanVO(
        Long id,
        String code,
        String name,
        Long monthlyCredits,
        String ext,
        List<SubscriptionSkuVO> skus,
        List<PlanEntitlementVO> entitlements,
        String status,
        Integer sort,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public record SubscriptionSkuVO(
            Long id,
            String skuCode,
            String billingCycle,
            Integer cycleMonths,
            Long price,
            Long marketPrice,
            String status,
            Integer sort) {}

    public record PlanEntitlementVO(
            String code,
            String name,
            String type,
            String unit,
            Long quota,
            String resetCycle,
            Long refillPrice) {}
}
