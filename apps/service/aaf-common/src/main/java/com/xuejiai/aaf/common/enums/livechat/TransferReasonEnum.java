package com.xuejiai.aaf.common.enums.livechat;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 会话转接原因枚举。 */
@Getter
@AllArgsConstructor
public enum TransferReasonEnum {
    SKILL_MISMATCH("skill_mismatch", "技能不匹配"),
    WORKLOAD("workload", "工作量过大"),
    USER_REQUEST("user_request", "用户要求"),
    ESCALATION("escalation", "问题升级"),
    SHIFT_CHANGE("shift_change", "换班交接");

    private final String code;
    private final String label;
}
