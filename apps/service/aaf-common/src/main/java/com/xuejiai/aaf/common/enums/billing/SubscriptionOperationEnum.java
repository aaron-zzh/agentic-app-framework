package com.xuejiai.aaf.common.enums.billing;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 订阅操作类型 */
@Getter
@AllArgsConstructor
public enum SubscriptionOperationEnum implements ArrayValuable<String> {
    NEW("NEW", "新购"),
    RENEW("RENEW", "续费");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(SubscriptionOperationEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
