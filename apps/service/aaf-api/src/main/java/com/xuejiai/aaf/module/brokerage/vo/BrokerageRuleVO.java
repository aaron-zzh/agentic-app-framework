package com.xuejiai.aaf.module.brokerage.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 佣金规则响应 VO。 */
public record BrokerageRuleVO(
        Long id,
        String name,
        String bizType,
        String bizTargetType,
        String bizTargetId,
        BigDecimal level1Rate,
        BigDecimal level2Rate,
        String calcBase,
        Long fixedAmount,
        Integer frozenDays,
        Integer priority,
        String status,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
