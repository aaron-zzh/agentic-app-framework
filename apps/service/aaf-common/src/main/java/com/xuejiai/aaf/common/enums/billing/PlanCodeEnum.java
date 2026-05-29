package com.xuejiai.aaf.common.enums.billing;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 订阅套餐编码 */
@Getter
@AllArgsConstructor
public enum PlanCodeEnum implements ArrayValuable<String> {
    FREE("FREE", "免费版"),
    PRO("PRO", "专业版"),
    TEAM("TEAM", "团队版"),
    ENTERPRISE("ENTERPRISE", "企业版");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(PlanCodeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
