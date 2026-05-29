package com.xuejiai.aaf.module.pay.vo;

import jakarta.validation.constraints.NotBlank;

/** 支付回调通知 */
public record PayNotifyDTO(@NotBlank String merchantOrderNo, @NotBlank String channelOrderNo, boolean success) {}
