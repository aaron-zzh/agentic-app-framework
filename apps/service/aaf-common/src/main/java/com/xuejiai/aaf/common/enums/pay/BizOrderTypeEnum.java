package com.xuejiai.aaf.common.enums.pay;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 业务订单类型枚举，对应字典 biz_order_type。 */
@Getter
@AllArgsConstructor
public enum BizOrderTypeEnum implements ArrayValuable<String> {
    RECHARGE("RECHARGE", "直接充值"),
    CREDIT_PACKAGE("CREDIT_PACKAGE", "积分套餐购买"),
    PURCHASE("PURCHASE", "购买"),
    SUBSCRIPTION("SUBSCRIPTION", "订阅");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(BizOrderTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
