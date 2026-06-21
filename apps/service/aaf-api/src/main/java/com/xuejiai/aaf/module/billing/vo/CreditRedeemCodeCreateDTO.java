package com.xuejiai.aaf.module.billing.vo;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Min;

public record CreditRedeemCodeCreateDTO(
        /** CREDIT 类型时必填且 >=1；MEMBERSHIP 类型时忽略，传 0 即可 */
        @Min(0) Long creditAmount,
        /** 积分类型：SUBSCRIPTION / TOPUP / REWARD / WEEKLY / MANUAL，默认 REWARD */
        String batchType,
        /** 兑换码类型：CREDIT=积分码（默认）/ MEMBERSHIP=会员码 */
        String type,
        /** 会员套餐 ID（type=MEMBERSHIP 时必填） */
        Long planId,
        LocalDateTime expiresAt,
        String remark) {}
