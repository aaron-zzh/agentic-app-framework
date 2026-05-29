package com.xuejiai.aaf.common.enums.billing;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 权益额度重置周期 */
@Getter
@AllArgsConstructor
public enum ResetCycleEnum {
    NONE("NONE", "不重置"),
    DAILY("DAILY", "每日"),
    MONTHLY("MONTHLY", "每月"),
    YEARLY("YEARLY", "每年");

    private final String code;
    private final String label;
}
