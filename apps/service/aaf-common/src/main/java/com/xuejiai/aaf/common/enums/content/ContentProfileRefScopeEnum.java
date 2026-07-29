package com.xuejiai.aaf.common.enums.content;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 品牌/IP 资料引用范围。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@AllArgsConstructor
public enum ContentProfileRefScopeEnum implements ArrayValuable<String> {
    PRIMARY("primary", "主资料"),
    AUXILIARY("auxiliary", "辅助资料");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ContentProfileRefScopeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
