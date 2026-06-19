package com.xuejiai.aaf.common.enums.chat;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 参与者角色枚举，对应字典 participant_role */
@Getter
@AllArgsConstructor
public enum ParticipantRoleEnum implements ArrayValuable<String> {
    OWNER("OWNER", "发起方"),
    MEMBER("MEMBER", "参与方"),
    OBSERVER("OBSERVER", "旁观者");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ParticipantRoleEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
