package com.xuejiai.aaf.module.system.file.enums;

import java.util.Locale;

/** 存储配置生命周期；退役配置只允许读取历史对象。 */
public enum FileConfigStatus {
    ACTIVE,
    RETIRED;

    public static FileConfigStatus parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
