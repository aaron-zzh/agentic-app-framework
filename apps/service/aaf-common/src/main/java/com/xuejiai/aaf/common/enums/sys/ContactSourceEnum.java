package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 联系人来源枚举，对应字典 sys_contact_source。 */
@Getter
@AllArgsConstructor
public enum ContactSourceEnum implements ArrayValuable<String> {
    REGISTER("REGISTER", "注册"),
    IMPORT("IMPORT", "导入"),
    CHANNEL("CHANNEL", "渠道接入"),
    VISITOR("VISITOR", "访客");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ContactSourceEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
