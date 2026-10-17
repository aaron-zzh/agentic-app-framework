package com.xuejiai.aaf.common.enums.device;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 设备部署区域。 */
@Getter
@AllArgsConstructor
public enum DeviceZoneEnum implements ArrayValuable<String> {
    LIVING_ROOM("LIVING_ROOM", "客厅"),
    BEDROOM("BEDROOM", "卧室"),
    BATHROOM_DOOR("BATHROOM_DOOR", "卫生间门口"),
    ENTRANCE("ENTRANCE", "入户门"),
    OTHER("OTHER", "其他");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(DeviceZoneEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
