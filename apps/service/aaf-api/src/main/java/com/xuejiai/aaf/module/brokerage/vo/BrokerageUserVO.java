package com.xuejiai.aaf.module.brokerage.vo;

import java.time.LocalDateTime;

/** 分销员响应 VO。 */
public record BrokerageUserVO(
        Long id,
        Long contactId,
        Long referrerContactId,
        LocalDateTime referrerBindTime,
        Boolean brokerageEnabled,
        LocalDateTime brokerageTime,
        Long balance,
        Long frozen,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
