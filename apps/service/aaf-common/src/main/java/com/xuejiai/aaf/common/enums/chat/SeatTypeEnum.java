package com.xuejiai.aaf.common.enums.chat;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 坐席类型枚举，对应字典 livechat_seat_type */
@Getter
@AllArgsConstructor
public enum SeatTypeEnum implements ArrayValuable<String> {
    HUMAN("HUMAN", "人工坐席"),
    AI("AI", "AI坐席");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(SeatTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
