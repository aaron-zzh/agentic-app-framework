package com.xuejiai.aaf.common.enums.stats;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 报表类型枚举。
 */
@Getter
@AllArgsConstructor
public enum ReportTypeEnum {

    DAILY("daily", "日报"),
    WEEKLY("weekly", "周报"),
    MONTHLY("monthly", "月报");

    private final String code;
    private final String label;
}
