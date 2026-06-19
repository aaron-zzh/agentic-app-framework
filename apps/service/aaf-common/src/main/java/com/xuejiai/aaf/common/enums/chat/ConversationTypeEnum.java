package com.xuejiai.aaf.common.enums.chat;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 会话类型枚举，对应字典 conversation_type */
@Getter
@AllArgsConstructor
public enum ConversationTypeEnum implements ArrayValuable<String> {
    AI("AI", "AI对话"),
    LIVECHAT("LIVECHAT", "客服会话"),
    IM("IM", "IM消息");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ConversationTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
