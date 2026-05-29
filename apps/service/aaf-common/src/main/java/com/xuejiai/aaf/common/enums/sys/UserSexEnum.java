package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 用户性别枚举，对应字典 sys_user_sex。 */
@Getter
@AllArgsConstructor
public enum UserSexEnum implements ArrayValuable<Integer> {
    UNKNOWN(0, "未知"),
    MALE(1, "男"),
    FEMALE(2, "女");

    private final Integer code;
    private final String label;

    public static final Integer[] ARRAYS =
            Arrays.stream(values()).map(UserSexEnum::getCode).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
