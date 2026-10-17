package com.xuejiai.aaf.common.enums.elder;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 预警事件状态。 */
@Getter
@AllArgsConstructor
public enum AlertStatusEnum implements ArrayValuable<String> {
    TRIGGERED("TRIGGERED", "已触发"),
    NOTIFYING("NOTIFYING", "通知中"),
    ACKED("ACKED", "已知晓"),
    RESOLVED("RESOLVED", "已处理"),
    FALSE_ALARM("FALSE_ALARM", "误报"),
    CANCELED("CANCELED", "已取消");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(AlertStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
