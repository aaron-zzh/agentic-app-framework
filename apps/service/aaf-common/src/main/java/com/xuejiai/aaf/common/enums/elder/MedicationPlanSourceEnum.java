package com.xuejiai.aaf.common.enums.elder;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 用药计划创建来源。 */
@Getter
@AllArgsConstructor
public enum MedicationPlanSourceEnum implements ArrayValuable<String> {
    MANAGEMENT("MANAGEMENT", "管理端"),
    CONVERSATION("CONVERSATION", "对话确认");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(MedicationPlanSourceEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
