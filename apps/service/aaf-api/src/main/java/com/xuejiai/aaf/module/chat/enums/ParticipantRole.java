package com.xuejiai.aaf.module.chat.enums;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 参与方角色。 */
@Getter
@AllArgsConstructor
public enum ParticipantRole implements ArrayValuable<String> {
    OWNER("OWNER", "发起方"),
    MEMBER("MEMBER", "参与方"),
    OBSERVER("OBSERVER", "旁观者");

    private final String role;
    private final String name;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ParticipantRole::getRole).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
