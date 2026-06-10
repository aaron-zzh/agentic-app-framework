package com.xuejiai.aaf.module.chat.enums;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 消息内容类型。 */
@Getter
@AllArgsConstructor
public enum MessageContentType implements ArrayValuable<String> {
    TEXT("TEXT", "文本"),
    IMAGE("IMAGE", "图片"),
    FILE("FILE", "文件"),
    TOOL_CALL("TOOL_CALL", "工具调用"),
    TOOL_RESULT("TOOL_RESULT", "工具结果"),
    TASK("TASK", "任务"),
    SYSTEM_EVENT("SYSTEM_EVENT", "系统事件");

    private final String type;
    private final String name;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(MessageContentType::getType).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
