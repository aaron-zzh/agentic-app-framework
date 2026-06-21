package com.xuejiai.aaf.module.brokerage.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.enums.brokerage.BrokerageWithdrawStatusEnum;
import com.xuejiai.aaf.common.enums.brokerage.BrokerageWithdrawTypeEnum;

/** 佣金提现响应 VO。 */
public record BrokerageWithdrawVO(
        Long id,
        Long contactId,
        Long amount,
        Long fee,
        BrokerageWithdrawTypeEnum type,
        String accountName,
        String accountNo,
        String qrCodeUrl,
        BrokerageWithdrawStatusEnum status,
        String auditReason,
        LocalDateTime auditTime,
        Long payTransferId,
        LocalDateTime transferTime,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
