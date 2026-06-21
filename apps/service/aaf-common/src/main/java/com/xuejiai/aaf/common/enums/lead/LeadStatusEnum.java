package com.xuejiai.aaf.common.enums.lead;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 访客线索处理状态枚举。 */
@Getter
@AllArgsConstructor
public enum LeadStatusEnum implements ArrayValuable<String> {
    NEW("NEW", "新线索"),
    PROCESSING("PROCESSING", "处理中"),
    RESOLVED("RESOLVED", "已处理"),
    SPAM("SPAM", "垃圾"),
    CLOSED("CLOSED", "已关闭");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(LeadStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
