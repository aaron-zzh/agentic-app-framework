package com.xuejiai.aaf.common.enums.aigc;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 分镜景别枚举，对应字典 aigc_shot_scene_type。 */
@Getter
@AllArgsConstructor
public enum AigcShotSceneTypeEnum implements ArrayValuable<String> {
    ELS("ELS", "远景"),
    LS("LS", "全景"),
    MS("MS", "中景"),
    MCU("MCU", "近景"),
    CU("CU", "特写"),
    ECU("ECU", "大特写");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(AigcShotSceneTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
