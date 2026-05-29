package com.xuejiai.aaf.module.pay.vo;

import java.time.LocalDateTime;

/** 退款单响应 */
public record RefundOrderVO(
        Long id,
        String refundNo,
        Long payOrderId,
        String merchantOrderNo,
        String channelCode,
        Long refundAmount,
        Integer status,
        String reason,
        String channelRefundNo,
        LocalDateTime successTime,
        LocalDateTime createTime) {}
