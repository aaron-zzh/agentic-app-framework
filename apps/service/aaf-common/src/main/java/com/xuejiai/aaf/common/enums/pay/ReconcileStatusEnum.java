package com.xuejiai.aaf.common.enums.pay;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 对账状态枚举 */
@Getter
@AllArgsConstructor
public enum ReconcileStatusEnum implements ArrayValuable<Integer> {
    PENDING(0, "待对账"),
    MATCHED(10, "已对平"),
    MISMATCHED(20, "存在差异");

    private final Integer code;
    private final String label;

    public static final Integer[] ARRAYS =
            Arrays.stream(values()).map(ReconcileStatusEnum::getCode).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
