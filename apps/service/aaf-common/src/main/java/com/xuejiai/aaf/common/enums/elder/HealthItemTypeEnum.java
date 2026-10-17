package com.xuejiai.aaf.common.enums.elder;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 长者健康条目类型。 */
@Getter
@AllArgsConstructor
public enum HealthItemTypeEnum implements ArrayValuable<String> {
    CHRONIC_DISEASE("CHRONIC_DISEASE", "慢性病"),
    ALLERGY("ALLERGY", "过敏"),
    MEDICATION("MEDICATION", "长期用药"),
    PAST_HISTORY("PAST_HISTORY", "既往史"),
    OTHER("OTHER", "其他");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(HealthItemTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
