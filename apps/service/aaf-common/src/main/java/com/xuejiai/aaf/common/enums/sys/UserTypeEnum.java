package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 用户类型枚举，对应字典 sys_user_type。 */
@Getter
@AllArgsConstructor
public enum UserTypeEnum implements ArrayValuable<Integer> {
    NORMAL(1, "普通用户"),
    ADMIN(2, "管理员");

    private final Integer code;
    private final String label;

    public static final Integer[] ARRAYS =
            Arrays.stream(values()).map(UserTypeEnum::getCode).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
