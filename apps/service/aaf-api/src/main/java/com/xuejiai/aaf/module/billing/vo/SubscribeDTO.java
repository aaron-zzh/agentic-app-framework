package com.xuejiai.aaf.module.billing.vo;

import jakarta.validation.constraints.NotBlank;

/** 订阅新购、升级或同 SKU 续费请求。 */
public record SubscribeDTO(@NotBlank String skuCode, @NotBlank String channelCode) {}
