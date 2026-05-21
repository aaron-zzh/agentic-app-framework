package com.xuejiai.aaf.common.enums.sys;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 用户类型枚举，对应字典 sys_user_type。 */
@Getter
@AllArgsConstructor
public enum UserTypeEnum {
    NORMAL(1, "普通用户"),
    ADMIN(2, "管理员");

    private final Integer code;
    private final String label;
}
