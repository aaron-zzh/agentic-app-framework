package com.xuejiai.aaf.module.company.ops.vo;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 运营指标创建入参。 */
public record OpsMetricCreateDTO(
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 64) String code,
        @NotNull BigDecimal value,
        @Size(max = 32) String unit,
        @Size(max = 64) String source) {}
