package com.xuejiai.aaf.common.enums.elder;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 康复指导分类。 */
@Getter
@AllArgsConstructor
public enum RehabGuideCategoryEnum implements ArrayValuable<String> {
    MOTOR_FUNCTION("MOTOR_FUNCTION", "运动功能"),
    DAILY_ACTIVITY("DAILY_ACTIVITY", "日常活动"),
    RESPIRATORY("RESPIRATORY", "呼吸训练"),
    COGNITIVE("COGNITIVE", "认知训练"),
    OTHER("OTHER", "其他");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(RehabGuideCategoryEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
