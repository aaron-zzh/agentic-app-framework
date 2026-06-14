package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 联系人状态枚举，对应字典 sys_contact_status。 */
@Getter
@AllArgsConstructor
public enum ContactStatusEnum implements ArrayValuable<String> {
    ACTIVE("ACTIVE", "活跃"),
    LEAD("LEAD", "线索"),
    VISITOR("VISITOR", "访客"),
    ARCHIVED("ARCHIVED", "已归档");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ContactStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
