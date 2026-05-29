package com.xuejiai.aaf.common.enums.billing;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 订阅状态 */
@Getter
@AllArgsConstructor
public enum SubscriptionStatusEnum {
    ACTIVE("ACTIVE", "生效中"),
    EXPIRED("EXPIRED", "已过期"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String label;
}
