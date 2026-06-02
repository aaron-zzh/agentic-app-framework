package com.xuejiai.aaf.module.billing.vo;

import jakarta.validation.constraints.NotBlank;

/** 订阅请求 */
public record SubscribeDTO(@NotBlank String planCode, @NotBlank String channelCode) {}
