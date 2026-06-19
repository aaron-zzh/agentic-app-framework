package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RoleCodeEnum implements ArrayValuable<String> {
    SUPER_ADMIN("super_admin", "超级管理员"),
    ADMIN("admin", "系统管理员"),
    ORG_ADMIN("org_admin", "组织管理员"),
    MEMBER("member", "普通成员"),
    GUEST("guest", "访客"),
    AGENT("agent", "AI 智能体"),
    SALES("sales", "销售");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(RoleCodeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
