package com.xuejiai.aaf.module.billing.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SubscriptionPlanCreateDTO(
        @NotBlank String code,
        @NotBlank String name,
        String status,
        Integer sort,
        @Min(0) Long monthlyCredits,
        String ext) {}
