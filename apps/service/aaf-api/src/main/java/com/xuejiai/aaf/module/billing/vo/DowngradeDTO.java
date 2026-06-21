package com.xuejiai.aaf.module.billing.vo;

import jakarta.validation.constraints.NotBlank;

/**
 * 降级订阅请求。
 *
 * <p>billingCycle 为 monthly 或 yearly（缺省视为 monthly），与 {@link SubscribeDTO} 保持一致。
 */
public record DowngradeDTO(@NotBlank String planCode, String billingCycle) {

    public boolean isYearly() {
        return "yearly".equalsIgnoreCase(billingCycle);
    }
}
