package com.xuejiai.aaf.common.enums.elder;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 服务项目状态。 */
@Getter
@AllArgsConstructor
public enum ServiceItemStatusEnum implements ArrayValuable<String> {
    AVAILABLE("AVAILABLE", "已上架"),
    UNAVAILABLE("UNAVAILABLE", "已下架");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ServiceItemStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
