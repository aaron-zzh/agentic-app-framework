package com.xuejiai.aaf.module.chat.enums;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 消息发送方类型。 */
@Getter
@AllArgsConstructor
public enum MessageSenderType implements ArrayValuable<String> {
    HUMAN("HUMAN", "用户"),
    ASSISTANT("ASSISTANT", "AI助理"),
    AGENT("AGENT", "智能体"),
    STAFF("STAFF", "人工坐席"),
    BOT("BOT", "机器人"),
    SYSTEM("SYSTEM", "系统");

    private final String type;
    private final String name;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(MessageSenderType::getType).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
