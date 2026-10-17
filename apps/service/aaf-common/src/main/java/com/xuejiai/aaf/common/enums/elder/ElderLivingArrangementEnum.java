package com.xuejiai.aaf.common.enums.elder;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 长者居住形态。 */
@Getter
@AllArgsConstructor
public enum ElderLivingArrangementEnum implements ArrayValuable<String> {
    ALONE("ALONE", "独居"),
    WITH_FAMILY("WITH_FAMILY", "与家人同住"),
    WITH_CAREGIVER("WITH_CAREGIVER", "与照护人同住");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ElderLivingArrangementEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
