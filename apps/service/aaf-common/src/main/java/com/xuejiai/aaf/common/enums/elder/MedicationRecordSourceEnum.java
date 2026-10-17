package com.xuejiai.aaf.common.enums.elder;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 服药记录来源。 */
@Getter
@AllArgsConstructor
public enum MedicationRecordSourceEnum implements ArrayValuable<String> {
    DEVICE("DEVICE", "设备确认"),
    CONTACT("CONTACT", "联系人代记"),
    OPERATOR("OPERATOR", "运营代记"),
    SYSTEM("SYSTEM", "系统判定");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(MedicationRecordSourceEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
