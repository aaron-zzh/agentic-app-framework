package com.xuejiai.aaf.common.enums.pay;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 积分流水类型枚举，对应字典 credit_transaction_type。 */
@Getter
@AllArgsConstructor
public enum CreditTransactionTypeEnum implements ArrayValuable<String> {
    EARN("EARN", "赚取"),
    SPEND("SPEND", "消费"),
    FREEZE("FREEZE", "冻结"),
    UNFREEZE("UNFREEZE", "解冻");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(CreditTransactionTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
