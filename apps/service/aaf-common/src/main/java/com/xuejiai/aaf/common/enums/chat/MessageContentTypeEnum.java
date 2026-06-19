package com.xuejiai.aaf.common.enums.chat;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 消息内容类型枚举，对应字典 message_content_type */
@Getter
@AllArgsConstructor
public enum MessageContentTypeEnum implements ArrayValuable<String> {
    TEXT("TEXT", "文本"),
    IMAGE("IMAGE", "图片"),
    FILE("FILE", "文件"),
    TOOL_CALL("TOOL_CALL", "工具调用"),
    TOOL_RESULT("TOOL_RESULT", "工具结果"),
    TASK("TASK", "任务"),
    SYSTEM_EVENT("SYSTEM_EVENT", "系统事件");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(MessageContentTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
