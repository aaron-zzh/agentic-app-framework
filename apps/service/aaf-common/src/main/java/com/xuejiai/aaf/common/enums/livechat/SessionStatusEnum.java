package com.xuejiai.aaf.common.enums.livechat;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 客服会话状态枚举。 */
@Getter
@AllArgsConstructor
public enum SessionStatusEnum implements ArrayValuable<String> {
    BOT("bot", "机器人服务中"),
    WAITING("waiting", "等待人工接入"),
    ACTIVE("active", "人工服务中"),
    CLOSED("closed", "已关闭");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(SessionStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
