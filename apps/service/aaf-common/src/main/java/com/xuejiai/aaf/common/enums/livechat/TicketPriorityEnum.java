package com.xuejiai.aaf.common.enums.livechat;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 工单优先级枚举。 */
@Getter
@AllArgsConstructor
public enum TicketPriorityEnum implements ArrayValuable<String> {
    LOW("low", "低", 72),
    MEDIUM("medium", "中", 48),
    HIGH("high", "高", 24),
    URGENT("urgent", "紧急", 4);

    private final String code;
    private final String label;

    /** SLA 时效（小时） */
    private final int slaHours;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(TicketPriorityEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
