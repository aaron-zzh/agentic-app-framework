package com.xuejiai.aaf.common.enums.chat;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 参与者离开原因枚举，对应字典 participant_left_reason */
@Getter
@AllArgsConstructor
public enum ParticipantLeftReasonEnum implements ArrayValuable<String> {
    TRANSFER("TRANSFER", "转接"),
    CLOSED("CLOSED", "会话关闭"),
    LEFT("LEFT", "主动离开");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ParticipantLeftReasonEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
