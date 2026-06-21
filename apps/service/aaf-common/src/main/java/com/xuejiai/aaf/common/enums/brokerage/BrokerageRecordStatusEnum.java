package com.xuejiai.aaf.common.enums.brokerage;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 佣金流水状态枚举，对应字典 brokerage_record_status。 */
@Getter
@AllArgsConstructor
public enum BrokerageRecordStatusEnum implements ArrayValuable<String> {
    FROZEN("FROZEN", "冻结中"),
    VALID("VALID", "可用"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(BrokerageRecordStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
