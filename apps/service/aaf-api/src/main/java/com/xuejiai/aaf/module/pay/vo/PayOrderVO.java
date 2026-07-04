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
        LocalDateTime createTime,
        /** 扫码支付二维码 URL（wx_native/alipay_qr 时有值） */
        String codeUrl,
        /** 关联业务订单类型（BizOrderTypeEnum.code，如 SUBSCRIPTION/CREDIT_PACKAGE），无关联业务订单时为 null */
        String bizOrderType) {}
