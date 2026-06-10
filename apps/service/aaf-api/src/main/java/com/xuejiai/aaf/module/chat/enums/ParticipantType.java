package com.xuejiai.aaf.module.chat.enums;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 参与方类型。 */
@Getter
@AllArgsConstructor
public enum ParticipantType implements ArrayValuable<String> {
    HUMAN("HUMAN", "用户"),
    ASSISTANT("ASSISTANT", "AI助理"),
    AGENT("AGENT", "智能体"),
    STAFF("STAFF", "人工坐席"),
    BOT("BOT", "机器人");

    private final String type;
    private final String name;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ParticipantType::getType).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
