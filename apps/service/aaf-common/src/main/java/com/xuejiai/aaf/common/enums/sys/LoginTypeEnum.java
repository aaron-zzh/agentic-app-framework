package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 登录方式枚举，对应字典 sys_login_type。 */
@Getter
@AllArgsConstructor
public enum LoginTypeEnum implements ArrayValuable<Integer> {
    USERNAME_PASSWORD(100, "账号密码登录"),
    OAUTH(101, "OAuth 登录"),
    EMAIL_CODE(102, "邮箱验证码登录"),
    LOGOUT(200, "主动登出"),
    FORCE_LOGOUT(202, "强制登出");

    private final Integer code;
    private final String label;

    public static final Integer[] ARRAYS =
            Arrays.stream(values()).map(LoginTypeEnum::getCode).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
