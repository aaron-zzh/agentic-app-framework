package com.xuejiai.aaf.module.pay.vo;

import java.time.LocalDateTime;

/** 支付单响应 */
public record PayOrderVO(
        Long id,
        String merchantOrderNo,
        String subject,
        Long amount,
        Integer status,
        String channelCode,
        String channelOrderNo,
        Long userId,
        LocalDateTime expireTime,
        LocalDateTime successTime,
        Long refundAmount,
        LocalDateTime createTime) {}
