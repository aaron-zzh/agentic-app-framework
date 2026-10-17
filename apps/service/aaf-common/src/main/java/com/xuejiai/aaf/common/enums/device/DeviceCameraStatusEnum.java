package com.xuejiai.aaf.common.enums.device;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 设备摄像头状态。 */
@Getter
@AllArgsConstructor
public enum DeviceCameraStatusEnum implements ArrayValuable<String> {
    AVAILABLE("AVAILABLE", "可用"),
    BLOCKED("BLOCKED", "被遮挡"),
    DISABLED("DISABLED", "已关闭"),
    UNKNOWN("UNKNOWN", "未知");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(DeviceCameraStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
