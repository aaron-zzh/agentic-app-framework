package com.xuejiai.aaf.common.enums.livechat;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 工单优先级枚举。 */
@Getter
@AllArgsConstructor
public enum TicketPriorityEnum {
    LOW("low", "低", 72),
    MEDIUM("medium", "中", 48),
    HIGH("high", "高", 24),
    URGENT("urgent", "紧急", 4);

    private final String code;
    private final String label;
    /** SLA 时效（小时） */
    private final int slaHours;
}
