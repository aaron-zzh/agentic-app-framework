package com.xuejiai.aaf.module.billing.vo;

import jakarta.validation.constraints.NotBlank;

/** 降级订阅请求；只记录目标 SKU，不立即收费。 */
public record DowngradeDTO(@NotBlank String skuCode) {}
