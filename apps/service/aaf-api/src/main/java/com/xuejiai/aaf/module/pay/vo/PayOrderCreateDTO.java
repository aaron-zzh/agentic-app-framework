package com.xuejiai.aaf.module.pay.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 创建支付单请求 */
public record PayOrderCreateDTO(
        @NotBlank String merchantOrderNo,
        @NotBlank String subject,
        String body,
        @NotNull @Min(1) Long amount,
        @NotBlank String channelCode,
        @NotNull Long userId) {}
