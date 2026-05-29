package com.xuejiai.aaf.framework.engine.settlement;

/** 退款结果 */
public record RefundResult(boolean success, String refundNo, String message) {}
