package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** ReBAC 对象级关系等级枚举，用于 sys_permission_tuple.relation。 */
@Getter
@AllArgsConstructor
public enum RebacRelationEnum implements ArrayValuable<String> {
    OWNER("OWNER", "拥有者"),
    EDITOR("EDITOR", "编辑者"),
    VIEWER("VIEWER", "查看者");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(RebacRelationEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
