package com.xuejiai.aaf.common.enums.sys;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 验证码场景枚举，对应字典 sys_verify_code_type，值与 SendCodeDTO.type 正则约束一致。 */
@Getter
@AllArgsConstructor
public enum VerifyCodeTypeEnum {
    REGISTER("register", "注册"),
    LOGIN("login", "登录"),
    RESET("reset", "重置密码");

    private final String code;
    private final String label;
}
