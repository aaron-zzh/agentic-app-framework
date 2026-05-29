package com.xuejiai.aaf.common.enums.livechat;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 消息发送者类型枚举。 */
@Getter
@AllArgsConstructor
public enum SenderTypeEnum implements ArrayValuable<String> {
    USER("user", "用户"),
    BOT("bot", "机器人"),
    STAFF("staff", "坐席");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(SenderTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
