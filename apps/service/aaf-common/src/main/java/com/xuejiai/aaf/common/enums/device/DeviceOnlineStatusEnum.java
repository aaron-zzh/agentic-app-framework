package com.xuejiai.aaf.common.enums.device;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 设备在线状态。 */
@Getter
@AllArgsConstructor
public enum DeviceOnlineStatusEnum implements ArrayValuable<String> {
    ONLINE("ONLINE", "在线"),
    SUSPECT("SUSPECT", "疑似离线"),
    OFFLINE("OFFLINE", "离线");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(DeviceOnlineStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
