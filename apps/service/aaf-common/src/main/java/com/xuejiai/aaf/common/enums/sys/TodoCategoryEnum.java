package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 待办分类枚举，对应字典 sys_todo_category，用于活动流待办事项。 */
@Getter
@AllArgsConstructor
public enum TodoCategoryEnum implements ArrayValuable<String> {
    TODO("todo", "待办"),
    CALL("call", "电话"),
    EMAIL("email", "邮件"),
    MEETING("meeting", "会议");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(TodoCategoryEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
