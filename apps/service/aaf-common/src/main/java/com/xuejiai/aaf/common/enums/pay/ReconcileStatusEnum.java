package com.xuejiai.aaf.common.enums.pay;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 对账状态枚举 */
@Getter
@AllArgsConstructor
public enum ReconcileStatusEnum {
    PENDING(0, "待对账"),
    MATCHED(10, "已对平"),
    MISMATCHED(20, "存在差异");

    private final Integer code;
    private final String label;
}
