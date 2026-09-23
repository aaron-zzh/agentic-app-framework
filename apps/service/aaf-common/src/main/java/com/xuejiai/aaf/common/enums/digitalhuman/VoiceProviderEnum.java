package com.xuejiai.aaf.common.enums.digitalhuman;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 音色供应商枚举，对应字典 digital_human_voice_provider */
@Getter
@AllArgsConstructor
public enum VoiceProviderEnum implements ArrayValuable<String> {
    ALIYUN("aliyun", "阿里云"),
    AZURE("azure", "Azure"),
    CUSTOM("custom", "自定义");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(VoiceProviderEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
