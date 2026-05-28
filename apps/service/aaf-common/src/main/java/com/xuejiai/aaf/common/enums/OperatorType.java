package com.xuejiai.aaf.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作者类型枚举。
 *
 * <p>区分操作由人类用户还是 AI 助理执行。
 */
@Getter
@AllArgsConstructor
public enum OperatorType {
    HUMAN("人类用户"),
    AI("AI 助理");

    private final String description;
}
