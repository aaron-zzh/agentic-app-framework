package com.xuejiai.aaf.common.enums.stats;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 统计时间粒度枚举。 */
@Getter
@AllArgsConstructor
public enum StatPeriodEnum implements ArrayValuable<String> {
    HOUR("hour", "小时"),
    DAY("day", "日"),
    WEEK("week", "周"),
    MONTH("month", "月");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(StatPeriodEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }

    /** 转为 PostgreSQL date_trunc 参数 */
    public String toDateTrunc() {
        return code;
    }
}
