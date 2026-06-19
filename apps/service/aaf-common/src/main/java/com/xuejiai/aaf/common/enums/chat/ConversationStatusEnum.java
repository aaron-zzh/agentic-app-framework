package com.xuejiai.aaf.common.enums.chat;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 会话状态枚举，对应字典 conversation_status */
@Getter
@AllArgsConstructor
public enum ConversationStatusEnum implements ArrayValuable<String> {
    ACTIVE("ACTIVE", "进行中"),
    ARCHIVED("ARCHIVED", "已归档"),
    BOT("BOT", "机器人服务"),
    WAITING("WAITING", "等待人工"),
    CLOSED("CLOSED", "已关闭");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ConversationStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
