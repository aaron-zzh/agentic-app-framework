package com.xuejiai.aaf.common.enums.pay;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 支付回调状态枚举，对应字典 pay_notify_status。 */
@Getter
@AllArgsConstructor
public enum PayNotifyStatusEnum {
    WAITING(0, "等待通知"),
    SUCCESS(10, "通知成功"),
    FAILURE(20, "通知失败");

    private final Integer code;
    private final String label;
}
