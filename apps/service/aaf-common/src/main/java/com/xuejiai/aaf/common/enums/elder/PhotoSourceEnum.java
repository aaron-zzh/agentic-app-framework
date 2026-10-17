package com.xuejiai.aaf.common.enums.elder;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 养老相册照片来源。 */
@Getter
@AllArgsConstructor
public enum PhotoSourceEnum implements ArrayValuable<String> {
    DEVICE_MANUAL("DEVICE_MANUAL", "机器人手动拍摄"),
    DEVICE_AUTO("DEVICE_AUTO", "机器人授权自动拍摄");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(PhotoSourceEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
