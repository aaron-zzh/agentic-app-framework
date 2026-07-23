package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 待办创建来源；仅手工创建的待办允许修改关联来源。 */
@Getter
@AllArgsConstructor
public enum TodoSourceTypeEnum implements ArrayValuable<String> {
    MANUAL("manual", "手工创建", true),
    COMMENT("comment", "评论创建", false),
    TASK("task", "任务创建", false);

    private final String code;
    private final String label;
    private final boolean sourceEditable;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(TodoSourceTypeEnum::getCode).toArray(String[]::new);

    public static boolean isSourceEditable(String code) {
        return Arrays.stream(values())
                .anyMatch(sourceType -> sourceType.code.equals(code) && sourceType.sourceEditable);
    }

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
