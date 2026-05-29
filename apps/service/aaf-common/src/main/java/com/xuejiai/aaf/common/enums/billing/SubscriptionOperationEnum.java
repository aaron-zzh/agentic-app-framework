package com.xuejiai.aaf.common.enums.billing;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 订阅操作类型 */
@Getter
@AllArgsConstructor
public enum SubscriptionOperationEnum {
    NEW("NEW", "新购"),
    RENEW("RENEW", "续费");

    private final String code;
    private final String label;
}
