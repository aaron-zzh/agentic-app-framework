package com.xuejiai.aaf.module.ai.output.domain.enums;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** AI 产出来源类型。 */
@Getter
@AllArgsConstructor
public enum OutputSourceType implements ArrayValuable<String> {
    AUTODEV("autodev", "智能开发"),
    TASK("task", "任务执行"),
    CHAT("chat", "对话产出"),
    TOOL("tool", "工具调用");

    private final String code;
    private final String name;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(OutputSourceType::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
