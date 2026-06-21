package com.xuejiai.aaf.common.enums.brokerage;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 佣金业务类型枚举，对应字典 brokerage_record_biz_type。 */
@Getter
@AllArgsConstructor
public enum BrokerageRecordBizTypeEnum implements ArrayValuable<String> {
    ORDER("ORDER", "商品订单"),
    SUBSCRIBE("SUBSCRIBE", "会员订阅"),
    RECHARGE("RECHARGE", "积分充值"),
    INVITE("INVITE", "邀请注册"),
    ORDER_REFUND("ORDER_REFUND", "退款冲回");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(BrokerageRecordBizTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
