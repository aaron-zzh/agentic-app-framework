package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 菜单类型枚举，对应字典 sys_menu_type。 */
@Getter
@AllArgsConstructor
public enum MenuTypeEnum implements ArrayValuable<String> {
    GROUP("GROUP", "分组"),
    MENU("MENU", "菜单"),
    BUTTON("BUTTON", "按钮");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(MenuTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
