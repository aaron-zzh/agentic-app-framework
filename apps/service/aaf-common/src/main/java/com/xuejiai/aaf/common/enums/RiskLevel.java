package com.xuejiai.aaf.common.enums;

import java.util.Arrays;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 风险等级枚举。 */
@Getter
@AllArgsConstructor
public enum RiskLevel implements ArrayValuable<String> {
    LOW("low", "低风险"),
    MEDIUM("medium", "中风险"),
    HIGH("high", "高风险");

    private final String code;
    private final String description;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(RiskLevel::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
