package com.xuejiai.aaf.common.enums.chat;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 消息发送方类型枚举，对应字典 message_sender_type */
@Getter
@AllArgsConstructor
public enum MessageSenderTypeEnum implements ArrayValuable<String> {
    HUMAN("HUMAN", "用户"),
    ASSISTANT("ASSISTANT", "AI助理"),
    AGENT("AGENT", "智能体"),
    STAFF("STAFF", "人工坐席"),
    BOT("BOT", "机器人"),
    SYSTEM("SYSTEM", "系统");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(MessageSenderTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
