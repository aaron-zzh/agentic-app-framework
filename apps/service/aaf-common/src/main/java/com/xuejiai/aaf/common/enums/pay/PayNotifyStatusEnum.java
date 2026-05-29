package com.xuejiai.aaf.common.enums.pay;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 支付回调状态枚举，对应字典 pay_notify_status。 */
@Getter
@AllArgsConstructor
public enum PayNotifyStatusEnum implements ArrayValuable<Integer> {
    WAITING(0, "等待通知"),
    SUCCESS(10, "通知成功"),
    FAILURE(20, "通知失败");

    private final Integer code;
    private final String label;

    public static final Integer[] ARRAYS =
            Arrays.stream(values()).map(PayNotifyStatusEnum::getCode).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
