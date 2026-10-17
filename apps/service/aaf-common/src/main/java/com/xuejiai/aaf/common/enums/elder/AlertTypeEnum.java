package com.xuejiai.aaf.common.enums.elder;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一期预警类型。 */
@Getter
@AllArgsConstructor
public enum AlertTypeEnum implements ArrayValuable<String> {
    FALL_SUSPECTED("FALL_SUSPECTED", "疑似跌倒"),
    HELP_REQUEST("HELP_REQUEST", "一键求助"),
    NO_ACTIVITY("NO_ACTIVITY", "长时间无活动"),
    DEVICE_OFFLINE("DEVICE_OFFLINE", "设备离线");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(AlertTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
