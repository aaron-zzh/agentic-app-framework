package com.xuejiai.aaf.common.enums.livechat;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 坐席状态枚举。 */
@Getter
@AllArgsConstructor
public enum SeatStatusEnum implements ArrayValuable<String> {
    ONLINE("online", "在线"),
    BUSY("busy", "忙碌"),
    OFFLINE("offline", "离线");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(SeatStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
