package com.xuejiai.aaf.common.enums.elder;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 用药计划状态。 */
@Getter
@AllArgsConstructor
public enum MedicationPlanStatusEnum implements ArrayValuable<String> {
    ACTIVE("ACTIVE", "启用"),
    INACTIVE("INACTIVE", "停用");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(MedicationPlanStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
