package com.xuejiai.aaf.module.billing.vo;

import jakarta.validation.constraints.Min;

public record SubscriptionPlanUpdateDTO(
        String name,
        @Min(0) Integer durationDays,
        @Min(0) Long price,
        @Min(0) Long marketPrice,
        String status,
        Integer sort,
        @Min(0) Long monthlyCredits,
        String ext) {}
