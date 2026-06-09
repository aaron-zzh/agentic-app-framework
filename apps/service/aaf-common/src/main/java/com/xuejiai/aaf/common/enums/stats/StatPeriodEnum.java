package com.xuejiai.aaf.common.enums.stats;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

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

    @JsonValue private final String code;
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

    /** 按 code 反序列化（JSON body + query string 统一入口） */
    @JsonCreator
    public static StatPeriodEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(e -> e.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown period: " + code));
    }
}
