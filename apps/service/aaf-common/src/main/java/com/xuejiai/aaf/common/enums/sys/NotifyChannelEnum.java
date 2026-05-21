package com.xuejiai.aaf.common.enums.sys;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 通知渠道枚举，对应字典 sys_notify_channel。 */
@Getter
@AllArgsConstructor
public enum NotifyChannelEnum {
    INTERNAL("INTERNAL", "站内信"),
    EMAIL("EMAIL", "邮件"),
    SMS("SMS", "短信");

    private final String code;
    private final String label;
}
