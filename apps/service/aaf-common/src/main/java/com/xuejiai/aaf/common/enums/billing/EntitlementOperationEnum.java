package com.xuejiai.aaf.common.enums.billing;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 权益额度变更操作类型 */
@Getter
@AllArgsConstructor
public enum EntitlementOperationEnum {
    USE("USE", "消费"),
    REFILL("REFILL", "充值"),
    RESET("RESET", "周期重置"),
    ADJUST("ADJUST", "人工调整");

    private final String code;
    private final String label;
}
