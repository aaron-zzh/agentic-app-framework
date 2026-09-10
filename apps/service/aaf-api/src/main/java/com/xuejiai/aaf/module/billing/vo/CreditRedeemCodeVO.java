package com.xuejiai.aaf.module.billing.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;

public record CreditRedeemCodeVO(
        Long id,
        String codePrefix,
        Long creditAmount,
        String batchType,
        String type,
        ResourceRefDTO sku,
        String skuCode,
        String billingCycle,
        ResourceRefDTO plan,
        String status,
        LocalDateTime expiresAt,
        ResourceRefDTO redeemedBy,
        LocalDateTime redeemedAt,
        String remark,
        LocalDateTime createTime) {}
