package com.xuejiai.aaf.module.brokerage.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.xuejiai.aaf.common.enums.brokerage.BrokerageRecordStatusEnum;

/** 佣金流水响应 VO。 */
public record BrokerageRecordVO(
        Long id,
        Long contactId,
        Long sourceContactId,
        Short sourceLevel,
        String bizType,
        String bizId,
        String title,
        Long amount,
        BrokerageRecordStatusEnum status,
        Integer frozenDays,
        LocalDateTime unfreezeTime,
        Long ruleId,
        BigDecimal appliedRate,
        Long calcBaseAmount,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
