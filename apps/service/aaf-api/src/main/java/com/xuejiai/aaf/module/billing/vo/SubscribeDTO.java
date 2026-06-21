package com.xuejiai.aaf.module.billing.vo;

import jakarta.validation.constraints.NotBlank;

/** 订阅请求 */
public record SubscribeDTO(
        @NotBlank String planCode,
        @NotBlank String channelCode,
        /** 计费周期：monthly（月付）/ yearly（年付） */
        String billingCycle) {

    public boolean isYearly() {
        return "yearly".equalsIgnoreCase(billingCycle);
    }
}
