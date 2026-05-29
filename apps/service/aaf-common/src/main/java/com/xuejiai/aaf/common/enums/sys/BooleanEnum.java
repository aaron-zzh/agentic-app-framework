package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 是否枚举，对应字典 sys_boolean。 */
@Getter
@AllArgsConstructor
public enum BooleanEnum implements ArrayValuable<String> {
    TRUE("true", "是"),
    FALSE("false", "否");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(BooleanEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
