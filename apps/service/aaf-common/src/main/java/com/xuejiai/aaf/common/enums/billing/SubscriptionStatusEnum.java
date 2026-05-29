package com.xuejiai.aaf.common.enums.billing;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 订阅状态 */
@Getter
@AllArgsConstructor
public enum SubscriptionStatusEnum implements ArrayValuable<String> {
    ACTIVE("ACTIVE", "生效中"),
    EXPIRED("EXPIRED", "已过期"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(SubscriptionStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
