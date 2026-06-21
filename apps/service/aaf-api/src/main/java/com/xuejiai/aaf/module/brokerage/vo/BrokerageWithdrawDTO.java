package com.xuejiai.aaf.module.brokerage.vo;

import com.xuejiai.aaf.common.enums.brokerage.BrokerageWithdrawStatusEnum;
import com.xuejiai.aaf.common.enums.brokerage.BrokerageWithdrawTypeEnum;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 佣金提现申请 DTO。创建时填提现信息，审核时填 status + auditReason。 */
public record BrokerageWithdrawDTO(
        @NotNull Long contactId,
        @NotNull @Positive Long amount,
        @NotNull BrokerageWithdrawTypeEnum type,
        String accountName,
        String accountNo,
        String qrCodeUrl,
        /** 审核时填写：APPROVED / REJECTED */
        BrokerageWithdrawStatusEnum status,
        /** 审核拒绝原因 */
        String auditReason) {}
