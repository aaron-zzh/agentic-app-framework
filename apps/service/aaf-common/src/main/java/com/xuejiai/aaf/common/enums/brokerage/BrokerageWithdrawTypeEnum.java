package com.xuejiai.aaf.common.enums.brokerage;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 佣金提现类型枚举，对应字典 brokerage_withdraw_type。 */
@Getter
@AllArgsConstructor
public enum BrokerageWithdrawTypeEnum implements ArrayValuable<String> {
    WECHAT("WECHAT", "微信"),
    ALIPAY("ALIPAY", "支付宝"),
    BANK("BANK", "银行卡");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(BrokerageWithdrawTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
