package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 联系人类型枚举，对应字典 sys_contact_type。 */
@Getter
@AllArgsConstructor
public enum ContactTypeEnum implements ArrayValuable<String> {
    PERSON("PERSON", "个人"),
    ORG("ORG", "组织");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ContactTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
