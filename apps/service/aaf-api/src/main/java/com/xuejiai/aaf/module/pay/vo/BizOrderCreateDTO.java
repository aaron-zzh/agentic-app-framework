package com.xuejiai.aaf.module.pay.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 创建业务订单请求 */
public record BizOrderCreateDTO(
        @NotBlank String orderType,
        @NotBlank String subject,
        @NotNull @Min(1) Long totalAmount,
        @NotBlank String channelCode) {}
