package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 登录结果枚举，对应字典 sys_login_result。 */
@Getter
@AllArgsConstructor
public enum LoginResultEnum implements ArrayValuable<Integer> {
    SUCCESS(0, "成功"),
    BAD_CREDENTIALS(10, "账号或密码错误"),
    USER_DISABLED(20, "账号被禁用"),
    VERIFY_CODE_INVALID(30, "验证码无效"),
    USER_LOCKED(40, "账号已锁定"),
    UNKNOWN_ERROR(100, "未知异常");

    private final Integer code;
    private final String label;

    public static final Integer[] ARRAYS =
            Arrays.stream(values()).map(LoginResultEnum::getCode).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
