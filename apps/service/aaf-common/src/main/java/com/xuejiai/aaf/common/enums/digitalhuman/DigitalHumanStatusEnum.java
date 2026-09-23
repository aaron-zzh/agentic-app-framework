package com.xuejiai.aaf.common.enums.digitalhuman;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 数字人状态枚举，对应字典 digital_human_status */
@Getter
@AllArgsConstructor
public enum DigitalHumanStatusEnum implements ArrayValuable<String> {
    ACTIVE("active", "活跃"),
    ARCHIVED("archived", "已归档");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(DigitalHumanStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
