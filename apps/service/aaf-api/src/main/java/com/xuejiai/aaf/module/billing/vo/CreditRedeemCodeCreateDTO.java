package com.xuejiai.aaf.module.billing.vo;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreditRedeemCodeCreateDTO(
        @NotNull @Min(1) Long creditAmount,
        /** 积分类型：SUBSCRIPTION / TOPUP / REWARD / WEEKLY / MANUAL，默认 REWARD */
        String batchType,
        LocalDateTime expiresAt,
        String remark) {}
