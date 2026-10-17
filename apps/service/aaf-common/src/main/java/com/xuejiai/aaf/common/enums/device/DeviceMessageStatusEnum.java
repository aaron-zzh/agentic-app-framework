package com.xuejiai.aaf.common.enums.device;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 设备可靠下行消息状态。 */
@Getter
@AllArgsConstructor
public enum DeviceMessageStatusEnum implements ArrayValuable<String> {
    QUEUED("QUEUED", "待下发"),
    DISPATCHED("DISPATCHED", "已下发"),
    ACCEPTED("ACCEPTED", "设备已接受"),
    SUCCEEDED("SUCCEEDED", "执行成功"),
    FAILED("FAILED", "执行失败"),
    EXPIRED("EXPIRED", "已过期"),
    CANCELLED("CANCELLED", "已撤销"),
    DELIVERY_UNKNOWN("DELIVERY_UNKNOWN", "送达未知");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(DeviceMessageStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
