package com.xuejiai.aaf.common.enums;

import java.util.Arrays;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用状态枚举。
 *
 * <p>0=正常 1=禁用
 */
@Getter
@AllArgsConstructor
public enum CommonStatusEnum implements ArrayValuable<Integer> {
    ENABLE(0, "正常"),
    DISABLE(1, "禁用");

    private final Integer code;
    private final String message;

    public static final Integer[] ARRAYS =
            Arrays.stream(values()).map(CommonStatusEnum::getCode).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
