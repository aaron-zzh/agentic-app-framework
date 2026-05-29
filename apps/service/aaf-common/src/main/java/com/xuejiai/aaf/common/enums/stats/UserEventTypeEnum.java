package com.xuejiai.aaf.common.enums.stats;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 用户行为事件类型枚举。 */
@Getter
@AllArgsConstructor
public enum UserEventTypeEnum implements ArrayValuable<String> {
    PAGE_VIEW("page_view", "页面浏览"),
    CLICK("click", "点击"),
    REGISTER("register", "注册"),
    ACTIVATE("activate", "激活"),
    PURCHASE("purchase", "付费"),
    LOGIN("login", "登录"),
    CHAT("chat", "对话"),
    TOOL_USE("tool_use", "工具使用");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(UserEventTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
