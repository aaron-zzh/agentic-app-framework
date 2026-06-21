package com.xuejiai.aaf.common.enums.lead;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 访客线索渠道枚举：表示访客的动作类型。 */
@Getter
@AllArgsConstructor
public enum LeadChannelEnum implements ArrayValuable<String> {
    VISIT("VISIT", "访问页面"),
    CHAT("CHAT", "匿名对话"),
    NEWSLETTER("NEWSLETTER", "订阅通知"),
    CONTACT("CONTACT", "联系我们"),
    FEEDBACK("FEEDBACK", "用户反馈");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(LeadChannelEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
