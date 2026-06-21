package com.xuejiai.aaf.module.billing.vo;

import java.time.LocalDateTime;

public record CreditRedeemCodeVO(
        Long id,
        String codePrefix,
        Long creditAmount,
        String batchType,
        String type,
        Long planId,
        String status,
        LocalDateTime expiresAt,
        Long redeemedByUserId,
        LocalDateTime redeemedAt,
        String remark,
        LocalDateTime createTime) {}
