package com.xuejiai.aaf.module.billing.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LevelCreateDTO(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull @Min(0) Integer expMin,
        @NotNull @Min(0) Integer expMax,
        String perks,
        @NotNull Integer sort) {}
