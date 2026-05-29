package com.xuejiai.aaf.module.pay.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 订单明细行创建请求 */
public record BizOrderItemCreateDTO(
        @NotBlank String productType,
        String productId,
        @NotBlank String productName,
        @NotNull @Min(1) Integer quantity,
        @NotNull @Min(0) Long unitPrice) {}
