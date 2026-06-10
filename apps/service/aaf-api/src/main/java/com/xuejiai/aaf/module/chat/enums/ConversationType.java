package com.xuejiai.aaf.module.chat.enums;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 会话类型。 */
@Getter
@AllArgsConstructor
public enum ConversationType implements ArrayValuable<String> {
    AI("AI", "AI对话"),
    LIVECHAT("LIVECHAT", "客服会话"),
    IM("IM", "IM消息");

    private final String type;
    private final String name;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ConversationType::getType).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
