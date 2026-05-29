package com.xuejiai.aaf.common.enums.livechat;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 工单状态枚举。 */
@Getter
@AllArgsConstructor
public enum TicketStatusEnum {
    PENDING("pending", "待处理"),
    PROCESSING("processing", "处理中"),
    CONFIRMING("confirming", "待确认"),
    CLOSED("closed", "已关闭");

    private final String code;
    private final String label;
}
