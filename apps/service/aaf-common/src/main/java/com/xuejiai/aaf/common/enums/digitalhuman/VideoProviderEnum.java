package com.xuejiai.aaf.common.enums.digitalhuman;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 视频提供商枚举，对应字典 digital_human_video_provider */
@Getter
@AllArgsConstructor
public enum VideoProviderEnum implements ArrayValuable<String> {
    ALIYUN("aliyun", "阿里云"),
    HYPERFRAMES("hyperframes", "Hyperframes");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(VideoProviderEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
