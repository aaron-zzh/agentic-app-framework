package com.xuejiai.aaf.module.billing.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubscriptionSkuCreateDTO(
        @NotNull Long planId,
        @NotBlank @Size(max = 64) String skuCode,
        @NotBlank @Size(max = 16) String billingCycle,
        @NotNull @Min(0) Integer cycleMonths,
        @NotNull @Min(0) Long price,
        @NotNull @Min(0) Long marketPrice,
        @Size(max = 16) String status,
        Integer sort,
        String ext) {}
