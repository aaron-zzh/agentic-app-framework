package com.xuejiai.aaf.framework.crud.definition;

import java.util.Objects;
import java.util.regex.Pattern;

/** 代码资源的稳定身份；仅代码 Definition 可声明。 */
public record ResourceKey(String value) {

    private static final Pattern PATTERN =
            Pattern.compile("^[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*)+$");

    public ResourceKey {
        value = Objects.requireNonNull(value, "value").trim();
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("非法资源标识: " + value);
        }
    }

    public static ResourceKey of(String value) {
        return new ResourceKey(value);
    }

    public String slug() {
        return value.substring(value.lastIndexOf('.') + 1);
    }
}
