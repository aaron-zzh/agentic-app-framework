package com.xuejiai.aaf.module.ai.output.domain.enums;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** AI 产出类别。 */
@Getter
@AllArgsConstructor
public enum OutputCategory implements ArrayValuable<String> {
    CODE("code", "代码"),
    DOCUMENT("document", "文档"),
    ENTITY_CHANGE("entity_change", "实体变更"),
    CONFIG("config", "配置"),
    FILE("file", "文件");

    private final String code;
    private final String name;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(OutputCategory::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
