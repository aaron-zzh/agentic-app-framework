package com.xuejiai.aaf.module.ai.aigc.media.enums;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 素材类型枚举。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@AllArgsConstructor
public enum MediaAssetType implements ArrayValuable<Integer> {
    IMAGE(1, "图片"),
    VIDEO(2, "视频"),
    AUDIO(3, "音频"),
    MODEL_3D(4, "3D 模型"),
    TEXT(5, "文案");

    private final Integer type;
    private final String name;

    public static final Integer[] ARRAYS =
            Arrays.stream(values()).map(MediaAssetType::getType).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
