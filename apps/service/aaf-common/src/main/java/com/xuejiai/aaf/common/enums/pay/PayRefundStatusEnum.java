package com.xuejiai.aaf.common.enums.pay;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 退款订单状态枚举，对应字典 pay_refund_status。 */
@Getter
@AllArgsConstructor
public enum PayRefundStatusEnum {
    WAITING(0, "等待退款"),
    SUCCESS(10, "退款成功"),
    FAILURE(20, "退款失败");

    private final Integer code;
    private final String label;
}
