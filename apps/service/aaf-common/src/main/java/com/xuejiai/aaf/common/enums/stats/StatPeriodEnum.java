package com.xuejiai.aaf.common.enums.stats;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统计时间粒度枚举。
 */
@Getter
@AllArgsConstructor
public enum StatPeriodEnum {

    HOUR("hour", "小时"),
    DAY("day", "日"),
    WEEK("week", "周"),
    MONTH("month", "月");

    private final String code;
    private final String label;

    /** 转为 PostgreSQL date_trunc 参数 */
    public String toDateTrunc() {
        return code;
    }
}
