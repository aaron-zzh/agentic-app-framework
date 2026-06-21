package com.xuejiai.aaf.module.billing.vo;

import java.time.LocalDateTime;

/**
 * 订阅信息视图。
 *
 * <p>新增字段（AAF-099 v0.2.0）：
 *
 * <ul>
 *   <li>autoRenew：自动续费意图位
 *   <li>cancelledAt：用户主动取消时间
 *   <li>pendingPlanCode/pendingYearly：降级排队信息
 *   <li>lastReminderAt：最近一次到期提醒时间
 * </ul>
 */
public record SubscriptionVO(
        Long id,
        String planCode,
        String planName,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String status,
        Boolean autoRenew,
        LocalDateTime cancelledAt,
        String pendingPlanCode,
        Boolean pendingYearly,
        LocalDateTime lastReminderAt) {}
