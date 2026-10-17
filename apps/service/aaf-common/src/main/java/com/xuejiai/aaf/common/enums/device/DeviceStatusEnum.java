package com.xuejiai.aaf.common.enums.device;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 设备绑定状态。 */
@Getter
@AllArgsConstructor
public enum DeviceStatusEnum implements ArrayValuable<String> {
    BOUND("BOUND", "已绑定"),
    UNBOUND("UNBOUND", "已解绑"),
    DISABLED("DISABLED", "已停用");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(DeviceStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
