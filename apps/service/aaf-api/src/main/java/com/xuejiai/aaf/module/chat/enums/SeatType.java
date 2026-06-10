package com.xuejiai.aaf.module.chat.enums;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 坐席类型。 */
@Getter
@AllArgsConstructor
public enum SeatType implements ArrayValuable<String> {
    HUMAN("HUMAN", "人工坐席"),
    AI("AI", "AI坐席");

    private final String type;
    private final String name;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(SeatType::getType).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
