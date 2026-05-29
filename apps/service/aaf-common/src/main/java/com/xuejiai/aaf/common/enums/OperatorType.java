package com.xuejiai.aaf.common.enums;

import java.util.Arrays;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作者类型枚举。
 *
 * <p>区分操作由人类用户还是 AI 助理执行。
 */
@Getter
@AllArgsConstructor
public enum OperatorType implements ArrayValuable<String> {
    HUMAN("human", "人类用户"),
    AI("ai", "AI 助理");

    private final String code;
    private final String description;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(OperatorType::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
