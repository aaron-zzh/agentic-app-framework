package com.xuejiai.aaf.common.enums.billing;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 权益类型枚举 */
@Getter
@AllArgsConstructor
public enum EntitlementTypeEnum {
    BOOLEAN("BOOLEAN", "开关型"),
    COUNTABLE("COUNTABLE", "计量型");

    private final String code;
    private final String label;
}
