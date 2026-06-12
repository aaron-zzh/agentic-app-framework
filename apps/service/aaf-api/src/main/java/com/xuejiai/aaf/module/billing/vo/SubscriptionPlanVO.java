package com.xuejiai.aaf.module.billing.vo;

import java.util.List;

/** 订阅套餐展示 VO（含年付价格和权益列表） */
public record SubscriptionPlanVO(
        Long id,
        String code,
        String name,
        Integer durationDays,
        /** 月付价格（分） */
        Long price,
        /** 年付价格（分）= price * 12 * 0.8 */
        Long yearlyPrice,
        /** 划线价（分） */
        Long marketPrice,
        /** 每月发放积分数 */
        Long monthlyCredits,
        String ext,
        List<PlanEntitlementVO> entitlements) {

    public record PlanEntitlementVO(
            String code,
            String name,
            String type,
            String unit,
            Long quota,
            String resetCycle,
            Long refillPrice) {}
}
