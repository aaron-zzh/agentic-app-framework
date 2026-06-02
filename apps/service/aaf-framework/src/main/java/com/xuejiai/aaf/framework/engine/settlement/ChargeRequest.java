package com.xuejiai.aaf.framework.engine.settlement;

/** 支付请求 */
public record ChargeRequest(
        String outTradeNo, long amount, String subject, String channelCode, String notifyUrl) {}
