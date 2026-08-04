package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AigcProjectUpdateDTO(
        String name,
        String description,
        String brief,
        String prompt,
        Long coverMediaVersionId,
        Long assistantId,
        BigDecimal budgetLimit,
        @NotNull @PositiveOrZero Integer expectedVersion) {}
