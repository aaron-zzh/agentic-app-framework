package com.xuejiai.aaf.framework.engine.settlement;

/**
 * 退款请求。
 *
 * @param outTradeNo 商户订单号（原支付单）
 * @param refundNo 退款单号，同时作为渠道侧幂等键（M26：同一退款重试必须复用同一 refundNo）
 * @param amount 本次退款金额（分）
 * @param originalAmount 原支付单总额（分）。M25：微信 V3 要求 amount.total 传原单总额、amount.refund 传退款额，
 *     以前用退款额顶替原单总额，部分退款会被渠道拒绝或按错误比例计算
 * @param reason 退款原因
 * @param channelCode 支付渠道编码
 */
public record RefundRequest(
        String outTradeNo,
        String refundNo,
        long amount,
        long originalAmount,
        String reason,
        String channelCode) {}
