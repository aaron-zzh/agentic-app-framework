package com.xuejiai.aaf.module.ai.chat.domain.enums;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 检查点作用域。 */
@Getter
@AllArgsConstructor
public enum CheckpointScope implements ArrayValuable<String> {
    COORDINATOR("coordinator", "协调者级"),
    SUBTASK("subtask", "子任务级"),
    AGENT_STEP("agent_step", "Agent 步骤级");

    private final String code;
    private final String description;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(CheckpointScope::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
