package com.xuejiai.aaf.module.pay.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 退款申请请求。
 *
 * @param payOrderId 支付单 ID
 * @param amount 退款金额（分）
 * @param reason 退款原因
 * @param requestNo M26：客户端幂等键。带上后超时重试会命中同一退款单，不会重复向渠道提交；不传则退化为每次新建（不推荐）
 */
public record RefundApplyDTO(
        @NotNull Long payOrderId, @NotNull @Min(1) Long amount, String reason, String requestNo) {}
