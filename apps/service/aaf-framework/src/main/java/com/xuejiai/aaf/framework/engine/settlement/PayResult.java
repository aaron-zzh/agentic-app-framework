package com.xuejiai.aaf.framework.engine.settlement;

/** 支付结果 */
public record PayResult(boolean success, String outTradeNo, String channelOrderNo, String message) {}
