package com.xuejiai.aaf.common.enums.developer;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DeveloperProxyStatusEnum implements ArrayValuable<String> {
    ACTIVE("ACTIVE", "启用"),
    DISABLED("DISABLED", "停用");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(DeveloperProxyStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
