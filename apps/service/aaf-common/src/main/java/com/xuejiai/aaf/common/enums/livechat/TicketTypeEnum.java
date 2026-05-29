package com.xuejiai.aaf.common.enums.livechat;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 工单类型枚举。 */
@Getter
@AllArgsConstructor
public enum TicketTypeEnum {
    CONSULTATION("consultation", "咨询"),
    COMPLAINT("complaint", "投诉"),
    BUG_REPORT("bug_report", "故障报告"),
    FEATURE_REQUEST("feature_request", "功能建议"),
    REFUND("refund", "退款"),
    OTHER("other", "其他");

    private final String code;
    private final String label;
}
