package com.xuejiai.aaf.module.chat.enums;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 参与方离开原因。 */
@Getter
@AllArgsConstructor
public enum ParticipantLeftReason implements ArrayValuable<String> {
    TRANSFER("TRANSFER", "转接"),
    CLOSED("CLOSED", "会话关闭"),
    LEFT("LEFT", "主动离开");

    private final String reason;
    private final String name;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ParticipantLeftReason::getReason).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
