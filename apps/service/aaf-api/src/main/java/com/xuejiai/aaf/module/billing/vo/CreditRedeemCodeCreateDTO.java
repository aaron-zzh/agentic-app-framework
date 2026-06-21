package com.xuejiai.aaf.module.billing.vo;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreditRedeemCodeCreateDTO(
        @NotNull @Min(1) Long creditAmount,
        /** 积分类型：SUBSCRIPTION / TOPUP / REWARD / WEEKLY / MANUAL，默认 REWARD */
        String batchType,
        /** 兑换码类型：CREDIT=积分码（默认）/ MEMBERSHIP=会员码 */
        String type,
        /** 会员套餐 ID（type=MEMBERSHIP 时必填） */
        Long planId,
        LocalDateTime expiresAt,
        String remark) {}
