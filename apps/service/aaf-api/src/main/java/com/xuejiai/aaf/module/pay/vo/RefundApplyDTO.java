package com.xuejiai.aaf.module.pay.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 退款申请请求 */
public record RefundApplyDTO(
        @NotNull Long payOrderId, @NotNull @Min(1) Long amount, String reason) {}
