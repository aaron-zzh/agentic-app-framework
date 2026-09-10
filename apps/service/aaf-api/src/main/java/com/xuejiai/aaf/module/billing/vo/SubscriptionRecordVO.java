package com.xuejiai.aaf.module.billing.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;

public record SubscriptionRecordVO(
        Long id,
        ResourceRefDTO user,
        ResourceRefDTO plan,
        ResourceRefDTO sku,
        String operation,
        Long payOrderId,
        Long skuPriceSnapshot,
        Long payPrice,
        String payStatus,
        LocalDateTime payTime,
        String fulfillmentStatus,
        Long checkoutGenerationId,
        LocalDateTime checkoutCalculatedAt,
        String checkoutValueSnapshot,
        Long serviceGenerationId,
        LocalDateTime serviceStartAt,
        LocalDateTime serviceEndAt,
        String valueStatus,
        Long supersededByRecordId,
        LocalDateTime supersededAt,
        String exceptionCode,
        String exceptionReason,
        LocalDateTime exceptionDetectedAt,
        LocalDateTime compensationResolvedAt,
        String compensationResult,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
