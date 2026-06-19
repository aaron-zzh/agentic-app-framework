package com.xuejiai.aaf.common.enums.developer;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DeveloperSubscriptionStatusEnum implements ArrayValuable<String> {
    ACTIVE("ACTIVE", "有效"),
    EXPIRED("EXPIRED", "已过期"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values())
                    .map(DeveloperSubscriptionStatusEnum::getCode)
                    .toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
