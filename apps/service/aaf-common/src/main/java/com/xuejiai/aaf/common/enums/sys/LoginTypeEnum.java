package com.xuejiai.aaf.common.enums.sys;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 登录方式枚举，对应字典 sys_login_type。 */
@Getter
@AllArgsConstructor
public enum LoginTypeEnum {
    USERNAME_PASSWORD(100, "账号密码登录"),
    OAUTH(101, "OAuth 登录"),
    EMAIL_CODE(102, "邮箱验证码登录"),
    LOGOUT(200, "主动登出"),
    FORCE_LOGOUT(202, "强制登出");

    private final Integer code;
    private final String label;
}
