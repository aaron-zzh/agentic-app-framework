package com.xuejiai.aaf.module.billing.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record SubscriptionSkuUpdateDTO(
        Long planId,
        @Size(max = 64) String skuCode,
        @Size(max = 16) String billingCycle,
        @Min(0) Integer cycleMonths,
        @Min(0) Long price,
        @Min(0) Long marketPrice,
        @Size(max = 16) String status,
        Integer sort,
        String ext) {}
