package com.xuejiai.aaf.common.enums.livechat;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 消息发送者类型枚举。 */
@Getter
@AllArgsConstructor
public enum SenderTypeEnum {
    USER("user", "用户"),
    BOT("bot", "机器人"),
    STAFF("staff", "坐席");

    private final String code;
    private final String label;
}
