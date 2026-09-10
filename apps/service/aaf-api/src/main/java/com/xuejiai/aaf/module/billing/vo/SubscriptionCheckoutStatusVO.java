package com.xuejiai.aaf.module.billing.vo;

import java.time.LocalDateTime;

/** 会员支付单对应的订阅履约状态。 */
public record SubscriptionCheckoutStatusVO(
        Long payOrderId,
        String payStatus,
        String fulfillmentStatus,
        String exceptionCode,
        LocalDateTime compensationResolvedAt,
        String compensationResult) {}
