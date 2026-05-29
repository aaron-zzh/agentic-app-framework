package com.xuejiai.aaf.common.enums.pay;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 支付通知类型枚举，对应字典 pay_notify_type。 */
@Getter
@AllArgsConstructor
public enum PayNotifyTypeEnum implements ArrayValuable<Integer> {
    ORDER(1, "支付单"),
    REFUND(2, "退款单");

    private final Integer code;
    private final String label;

    public static final Integer[] ARRAYS =
            Arrays.stream(values()).map(PayNotifyTypeEnum::getCode).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
