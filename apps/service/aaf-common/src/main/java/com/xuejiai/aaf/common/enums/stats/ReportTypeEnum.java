package com.xuejiai.aaf.common.enums.stats;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 报表类型枚举。 */
@Getter
@AllArgsConstructor
public enum ReportTypeEnum implements ArrayValuable<String> {
    DAILY("daily", "日报"),
    WEEKLY("weekly", "周报"),
    MONTHLY("monthly", "月报");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ReportTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
