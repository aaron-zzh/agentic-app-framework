package com.xuejiai.aaf.common.enums.brokerage;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 佣金提现状态枚举，对应字典 brokerage_withdraw_status。 */
@Getter
@AllArgsConstructor
public enum BrokerageWithdrawStatusEnum implements ArrayValuable<String> {
    PENDING("PENDING", "待审核"),
    APPROVED("APPROVED", "审核通过"),
    REJECTED("REJECTED", "审核拒绝"),
    TRANSFERRED("TRANSFERRED", "已转账");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values())
                    .map(BrokerageWithdrawStatusEnum::getCode)
                    .toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
