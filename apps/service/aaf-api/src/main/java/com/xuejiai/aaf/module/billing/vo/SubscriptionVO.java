package com.xuejiai.aaf.module.billing.vo;

import java.time.LocalDateTime;

/** 订阅信息视图 */
public record SubscriptionVO(
        Long id,
        String planCode,
        String planName,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String status) {
}
