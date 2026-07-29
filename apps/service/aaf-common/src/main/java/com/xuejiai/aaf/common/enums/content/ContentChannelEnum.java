package com.xuejiai.aaf.common.enums.content;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内容发布渠道。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@AllArgsConstructor
public enum ContentChannelEnum implements ArrayValuable<String> {
    XIAOHONGSHU("xiaohongshu", "小红书"),
    DOUYIN("douyin", "抖音"),
    WECHAT_CHANNELS("wechat_channels", "视频号"),
    WECHAT_MP("wechat_mp", "公众号"),
    OFFLINE_POSTER("offline_poster", "线下海报"),
    BILIBILI("bilibili", "哔哩哔哩");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ContentChannelEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
