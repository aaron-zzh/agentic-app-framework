package com.xuejiai.aaf.common.enums.pay;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 支付订单状态枚举，对应字典 pay_order_status。 */
@Getter
@AllArgsConstructor
public enum PayOrderStatusEnum {
    WAITING(0, "等待支付"),
    SUCCESS(10, "支付成功"),
    CLOSED(30, "支付关闭");

    private final Integer code;
    private final String label;
}
