package com.xuejiai.aaf.module.chat.enums;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 会话状态。AI：ACTIVE/ARCHIVED；LIVECHAT：BOT/WAITING/ACTIVE/CLOSED；IM：ACTIVE/CLOSED。 */
@Getter
@AllArgsConstructor
public enum ConversationStatus implements ArrayValuable<String> {
    ACTIVE("ACTIVE", "进行中"),
    ARCHIVED("ARCHIVED", "已归档"),
    BOT("BOT", "机器人服务"),
    WAITING("WAITING", "等待人工"),
    CLOSED("CLOSED", "已关闭");

    private final String status;
    private final String name;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ConversationStatus::getStatus).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
