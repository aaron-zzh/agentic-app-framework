package com.xuejiai.aaf.common.enums.pay;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 对账差异类型枚举 */
@Getter
@AllArgsConstructor
public enum ReconcileDiffTypeEnum implements ArrayValuable<String> {
    AMOUNT_MISMATCH("AMOUNT_MISMATCH", "金额不一致"),
    STATUS_MISMATCH("STATUS_MISMATCH", "状态不一致"),
    LOCAL_ONLY("LOCAL_ONLY", "本地有渠道无"),
    CHANNEL_ONLY("CHANNEL_ONLY", "渠道有本地无");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ReconcileDiffTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
