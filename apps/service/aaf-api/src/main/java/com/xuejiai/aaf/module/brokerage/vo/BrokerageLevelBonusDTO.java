package com.xuejiai.aaf.module.brokerage.vo;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

/** 会员等级佣金加成创建/更新 DTO。 */
public record BrokerageLevelBonusDTO(
        @NotNull Long ruleId,
        @NotNull Long planId,
        @NotNull BigDecimal level1Rate,
        @NotNull BigDecimal level2Rate) {}
