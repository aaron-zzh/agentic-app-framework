package com.xuejiai.aaf.module.brokerage.vo;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 佣金规则创建/更新 DTO。 */
public record BrokerageRuleDTO(
        @NotBlank String name,
        @NotBlank String bizType,
        String bizTargetType,
        String bizTargetId,
        @NotNull BigDecimal level1Rate,
        @NotNull BigDecimal level2Rate,
        String calcBase,
        Long fixedAmount,
        Integer frozenDays,
        Integer priority,
        String status) {}
