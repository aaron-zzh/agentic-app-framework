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
        LocalDateTime startAt,
        LocalDateTime endAt,
        String status,
        Boolean autoRenew,
        LocalDateTime cancelledAt,
        String pendingPlanCode,
        Boolean pendingYearly,
        LocalDateTime lastReminderAt,
        Long sourceId,
        LocalDateTime createTime) {}
