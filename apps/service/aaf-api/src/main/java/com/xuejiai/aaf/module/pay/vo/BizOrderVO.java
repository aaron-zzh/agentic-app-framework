package com.xuejiai.aaf.module.pay.vo;

import java.time.LocalDateTime;

/** 业务订单响应 */
public record BizOrderVO(
        Long id,
        String orderNo,
        Long userId,
        String orderType,
        String subject,
        Long totalAmount,
        Long payOrderId,
        String status,
        LocalDateTime createTime) {}
