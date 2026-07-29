package com.xuejiai.aaf.common.enums.content;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内容项目关系图层。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@AllArgsConstructor
public enum ContentRelationLayerEnum implements ArrayValuable<String> {
    DOMAIN("domain", "领域关系"),
    REFERENCE("reference", "引用关系"),
    STORY_ORDER("story_order", "故事顺序"),
    EXECUTION("execution", "执行依赖");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ContentRelationLayerEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
