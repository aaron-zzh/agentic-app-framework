package com.xuejiai.aaf.common.enums.device;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 设备类型。 */
@Getter
@AllArgsConstructor
public enum DeviceTypeEnum implements ArrayValuable<String> {
    ROBOT("ROBOT", "陪伴机器人"),
    CAMERA_NODE("CAMERA_NODE", "固定摄像头节点");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(DeviceTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
