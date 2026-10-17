package com.xuejiai.aaf.common.enums.device;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 设备下行消息方向。 */
@Getter
@AllArgsConstructor
public enum DeviceMessageDirectionEnum implements ArrayValuable<String> {
    COMMAND("COMMAND", "指令"),
    CANCEL("CANCEL", "撤销"),
    CONFIG("CONFIG", "配置");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(DeviceMessageDirectionEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
