package com.xuejiai.aaf.common.enums.aigc;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 时间轴轨道类型枚举，对应字典 aigc_track_type。 */
@Getter
@AllArgsConstructor
public enum AigcTrackTypeEnum implements ArrayValuable<String> {
    VIDEO("VIDEO", "视频轨"),
    AUDIO("AUDIO", "音频轨"),
    SUBTITLE("SUBTITLE", "字幕轨"),
    STICKER("STICKER", "贴图轨");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(AigcTrackTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
