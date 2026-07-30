package com.xuejiai.aaf.common.enums.content;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内容对象版本状态。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@AllArgsConstructor
public enum ContentObjectVersionStatusEnum implements ArrayValuable<String> {
    CANDIDATE("candidate", "候选"),
    ADOPTED("adopted", "已采用"),
    REJECTED("rejected", "已否决"),
    SUPERSEDED("superseded", "已被取代");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values())
                    .map(ContentObjectVersionStatusEnum::getCode)
                    .toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
