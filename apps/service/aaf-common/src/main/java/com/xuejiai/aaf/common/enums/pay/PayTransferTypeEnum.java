package com.xuejiai.aaf.common.enums.pay;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 转账类型枚举，对应字典 pay_transfer_type。 */
@Getter
@AllArgsConstructor
public enum PayTransferTypeEnum implements ArrayValuable<Integer> {
    ALIPAY(1, "支付宝余额"),
    WECHAT(2, "微信余额"),
    BANK_CARD(3, "银行卡"),
    WALLET(4, "钱包余额");

    private final Integer code;
    private final String label;

    public static final Integer[] ARRAYS =
            Arrays.stream(values()).map(PayTransferTypeEnum::getCode).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
