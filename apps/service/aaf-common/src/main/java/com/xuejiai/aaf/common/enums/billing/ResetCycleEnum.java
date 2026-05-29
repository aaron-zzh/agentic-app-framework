package com.xuejiai.aaf.common.enums.billing;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 权益额度重置周期 */
@Getter
@AllArgsConstructor
public enum ResetCycleEnum implements ArrayValuable<String> {
    NONE("NONE", "不重置"),
    DAILY("DAILY", "每日"),
    MONTHLY("MONTHLY", "每月"),
    YEARLY("YEARLY", "每年");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ResetCycleEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
