package com.xuejiai.aaf.module.company.okr.vo;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 关键结果创建入参。 */
public record KeyResultCreateDTO(
        @NotBlank @Size(max = 256) String title,
        @NotBlank @Size(max = 16) String metricType,
        BigDecimal startValue,
        BigDecimal targetValue,
        BigDecimal currentValue,
        Long ownerUserId) {}
