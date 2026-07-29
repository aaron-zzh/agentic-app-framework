package com.xuejiai.aaf.common.enums.content;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内容项目关系类型。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@AllArgsConstructor
public enum ContentRelationTypeEnum implements ArrayValuable<String> {
    CONTAINS("contains", "包含"),
    CONSTRAINS("constrains", "约束"),
    DERIVES("derives", "派生"),
    REFERENCES("references", "引用"),
    COMPOSES("composes", "组成"),
    ORDERS("orders", "顺序"),
    VARIANT("variant", "变体"),
    EXECUTION_DEPENDS("execution_depends", "执行依赖");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ContentRelationTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
