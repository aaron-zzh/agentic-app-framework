package com.xuejiai.aaf.module.pay.vo;

/** 订单明细行响应 */
public record BizOrderItemVO(
        Long id,
        Long orderId,
        String productType,
        String productId,
        String productName,
        Integer quantity,
        Long unitPrice,
        Long totalPrice) {}
