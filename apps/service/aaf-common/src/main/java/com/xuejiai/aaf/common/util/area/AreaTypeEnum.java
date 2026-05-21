package com.xuejiai.aaf.common.util.area;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 区域类型枚举。 */
@Getter
@AllArgsConstructor
public enum AreaTypeEnum {
    COUNTRY(1, "国家"),
    PROVINCE(2, "省份"),
    CITY(3, "城市"),
    DISTRICT(4, "地区");

    private final Integer type;
    private final String name;
}
