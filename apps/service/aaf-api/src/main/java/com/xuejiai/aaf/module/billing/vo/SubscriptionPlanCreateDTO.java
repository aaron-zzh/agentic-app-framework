package com.xuejiai.aaf.module.billing.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubscriptionPlanCreateDTO(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull @Min(0) Integer durationDays,
        @NotNull @Min(0) Long price,
        @NotNull @Min(0) Long marketPrice,
        String status,
        Integer sort,
        @Min(0) Long monthlyCredits,
        String ext) {}
