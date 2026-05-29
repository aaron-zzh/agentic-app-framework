package com.xuejiai.aaf.common.enums.channel;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道消息类型枚举。 */
@Getter
@AllArgsConstructor
public enum MessageTypeEnum implements ArrayValuable<String> {
    TEXT("text", "文本"),
    IMAGE("image", "图片"),
    VOICE("voice", "语音"),
    VIDEO("video", "视频"),
    EVENT("event", "事件"),
    TEMPLATE("template", "模板消息"),
    MARKDOWN("markdown", "Markdown"),
    CARD("card", "卡片消息");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(MessageTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
