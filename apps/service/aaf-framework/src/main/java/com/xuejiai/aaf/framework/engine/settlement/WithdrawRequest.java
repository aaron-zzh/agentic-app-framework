package com.xuejiai.aaf.framework.engine.settlement;

/** 提现打款请求 */
public record WithdrawRequest(String outTradeNo, Long userId, long amount, String channelCode) {}
