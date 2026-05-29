package com.xuejiai.aaf.common.enums.livechat;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 客服会话状态枚举。 */
@Getter
@AllArgsConstructor
public enum SessionStatusEnum {
    BOT("bot", "机器人服务中"),
    WAITING("waiting", "等待人工接入"),
    ACTIVE("active", "人工服务中"),
    CLOSED("closed", "已关闭");

    private final String code;
    private final String label;
}
