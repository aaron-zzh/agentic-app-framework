package com.xuejiai.aaf.common.enums.elder;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 预警响应渠道。 */
@Getter
@AllArgsConstructor
public enum AlertHandlingChannelEnum implements ArrayValuable<String> {
    INTERNAL("INTERNAL", "站内信"),
    DINGTALK("DINGTALK", "钉钉群"),
    SMS("SMS", "短信"),
    MANUAL("MANUAL", "人工操作");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(AlertHandlingChannelEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
