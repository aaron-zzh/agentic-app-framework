package com.xuejiai.aaf.common.enums.content;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内容项目对象来源。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@AllArgsConstructor
public enum ContentObjectSourceEnum implements ArrayValuable<String> {
    BLUEPRINT("blueprint", "蓝图"),
    USER("user", "用户"),
    ASSISTANT("assistant", "助手"),
    WORKFLOW("workflow", "工作流"),
    IMPORT("import", "导入");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ContentObjectSourceEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
