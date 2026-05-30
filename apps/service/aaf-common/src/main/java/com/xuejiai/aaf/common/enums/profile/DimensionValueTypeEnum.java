package com.xuejiai.aaf.common.enums.profile;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 画像维度值类型枚举。 */
@Getter
@AllArgsConstructor
public enum DimensionValueTypeEnum implements ArrayValuable<String> {
    TEXT("text", "文本"),
    NUMBER("number", "数值"),
    BOOLEAN("boolean", "布尔"),
    ENUM("enum", "枚举"),
    TAGS("tags", "标签数组"),
    JSON("json", "JSON对象");

    private final String code;
    private final String label;

    @Override
    public String[] array() {
        return java.util.Arrays.stream(values()).map(DimensionValueTypeEnum::getCode).toArray(String[]::new);
    }
}
