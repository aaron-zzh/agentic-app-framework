package com.xuejiai.aaf.module.billing.vo;

import jakarta.validation.constraints.Min;

public record SubscriptionPlanUpdateDTO(
        String name, String status, Integer sort, @Min(0) Long monthlyCredits, String ext) {}
