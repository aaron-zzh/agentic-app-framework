package com.xuejiai.aaf.common.enums.chat;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 参与者类型枚举，对应字典 participant_type */
@Getter
@AllArgsConstructor
public enum ParticipantTypeEnum implements ArrayValuable<String> {
    HUMAN("HUMAN", "用户"),
    ASSISTANT("ASSISTANT", "AI助理"),
    AGENT("AGENT", "智能体"),
    STAFF("STAFF", "人工坐席"),
    BOT("BOT", "机器人");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ParticipantTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
