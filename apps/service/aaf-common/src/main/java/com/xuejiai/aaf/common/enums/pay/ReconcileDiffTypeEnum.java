package com.xuejiai.aaf.common.enums.pay;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 对账差异类型枚举 */
@Getter
@AllArgsConstructor
public enum ReconcileDiffTypeEnum {
    AMOUNT_MISMATCH("AMOUNT_MISMATCH", "金额不一致"),
    STATUS_MISMATCH("STATUS_MISMATCH", "状态不一致"),
    LOCAL_ONLY("LOCAL_ONLY", "本地有渠道无"),
    CHANNEL_ONLY("CHANNEL_ONLY", "渠道有本地无");

    private final String code;
    private final String label;
}
