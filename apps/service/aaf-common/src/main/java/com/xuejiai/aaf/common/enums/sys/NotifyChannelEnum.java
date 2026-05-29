package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 通知渠道枚举，对应字典 sys_notify_channel。 */
@Getter
@AllArgsConstructor
public enum NotifyChannelEnum implements ArrayValuable<String> {
    INTERNAL("INTERNAL", "站内信"),
    EMAIL("EMAIL", "邮件"),
    SMS("SMS", "短信");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(NotifyChannelEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
