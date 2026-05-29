package com.xuejiai.aaf.common.enums.billing;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 权益类型枚举 */
@Getter
@AllArgsConstructor
public enum EntitlementTypeEnum implements ArrayValuable<String> {
    BOOLEAN("BOOLEAN", "开关型"),
    COUNTABLE("COUNTABLE", "计量型");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(EntitlementTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
