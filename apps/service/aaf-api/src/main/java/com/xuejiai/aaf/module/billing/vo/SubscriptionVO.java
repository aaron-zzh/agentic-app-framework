package com.xuejiai.aaf.module.billing.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;

/** 用户订阅实例视图。 */
public record SubscriptionVO(
        Long id,
        ResourceRefDTO user,
        ResourceRefDTO plan,
        String planCode,
        String planName,
        String skuCode,
        String billingCycle,
        Integer cycleMonths,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String status,
        LocalDateTime cancelledAt,
        String pendingPlanName,
        String pendingSkuCode,
        String pendingBillingCycle,
        LocalDateTime lastReminderAt,
        Long sourceId,
        LocalDateTime createTime) {}
