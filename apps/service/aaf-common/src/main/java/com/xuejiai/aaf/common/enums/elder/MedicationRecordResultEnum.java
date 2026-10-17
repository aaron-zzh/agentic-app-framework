package com.xuejiai.aaf.common.enums.elder;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 服药记录结果。 */
@Getter
@AllArgsConstructor
public enum MedicationRecordResultEnum implements ArrayValuable<String> {
    TAKEN("TAKEN", "已服"),
    MISSED("MISSED", "漏服"),
    SKIPPED("SKIPPED", "跳过");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(MedicationRecordResultEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
