package com.xuejiai.aaf.common.enums.pay;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 订单明细产品类型枚举，对应字典 product_type。 */
@Getter
@AllArgsConstructor
public enum ProductTypeEnum implements ArrayValuable<String> {
    CREDIT_PACK("CREDIT_PACK", "积分套餐"),
    TOKEN_PACK("TOKEN_PACK", "Token 套餐"),
    SUBSCRIPTION("SUBSCRIPTION", "订阅服务"),
    AGENT("AGENT", "Agent"),
    TOOL("TOOL", "工具"),
    KNOWLEDGE("KNOWLEDGE", "知识库");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ProductTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
