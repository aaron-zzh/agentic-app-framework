package com.xuejiai.aaf.framework.engine.settlement;

/** 退款请求 */
public record RefundRequest(
        String outTradeNo, String refundNo, long amount, String reason, String channelCode) {}
