package com.xuejiai.aaf.common.enums.channel;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道类型枚举。 */
@Getter
@AllArgsConstructor
public enum ChannelTypeEnum implements ArrayValuable<String> {
    WECHAT_MP("wechat_mp", "微信公众号"),
    WECHAT_MINI("wechat_mini", "微信小程序"),
    DINGTALK("dingtalk", "钉钉"),
    FEISHU("feishu", "飞书"),
    WEBHOOK("webhook", "Webhook"),
    WEB("web", "网页");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ChannelTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
