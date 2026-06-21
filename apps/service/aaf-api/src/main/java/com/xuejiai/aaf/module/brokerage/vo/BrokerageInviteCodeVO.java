package com.xuejiai.aaf.module.brokerage.vo;

import java.time.LocalDateTime;

/** 邀请码响应 VO。 */
public record BrokerageInviteCodeVO(
        Long id,
        Long contactId,
        String code,
        String channel,
        Integer usedCount,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
