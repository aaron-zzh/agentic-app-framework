package com.xuejiai.aaf.common.enums.sys;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 是否枚举，对应字典 sys_boolean。 */
@Getter
@AllArgsConstructor
public enum BooleanEnum {
    TRUE("true", "是"),
    FALSE("false", "否");

    private final String code;
    private final String label;
}
