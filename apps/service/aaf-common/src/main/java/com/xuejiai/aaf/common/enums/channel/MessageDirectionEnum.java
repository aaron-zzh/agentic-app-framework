package com.xuejiai.aaf.common.enums.channel;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 消息方向枚举。 */
@Getter
@AllArgsConstructor
public enum MessageDirectionEnum implements ArrayValuable<String> {
    INBOUND("inbound", "入站（用户→系统）"),
    OUTBOUND("outbound", "出站（系统→用户）");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(MessageDirectionEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
