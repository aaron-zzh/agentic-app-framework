package com.xuejiai.aaf.common.enums.livechat;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 会话转接原因枚举。 */
@Getter
@AllArgsConstructor
public enum TransferReasonEnum implements ArrayValuable<String> {
    SKILL_MISMATCH("skill_mismatch", "技能不匹配"),
    WORKLOAD("workload", "工作量过大"),
    USER_REQUEST("user_request", "用户要求"),
    ESCALATION("escalation", "问题升级"),
    SHIFT_CHANGE("shift_change", "换班交接");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(TransferReasonEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
