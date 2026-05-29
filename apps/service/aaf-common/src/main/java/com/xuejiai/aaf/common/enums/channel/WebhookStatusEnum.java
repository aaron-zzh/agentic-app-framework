package com.xuejiai.aaf.common.enums.channel;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Webhook 状态枚举。 */
@Getter
@AllArgsConstructor
public enum WebhookStatusEnum implements ArrayValuable<String> {
    ACTIVE("active", "启用"),
    INACTIVE("inactive", "停用"),
    FAILED("failed", "失败（多次推送失败自动停用）");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(WebhookStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
