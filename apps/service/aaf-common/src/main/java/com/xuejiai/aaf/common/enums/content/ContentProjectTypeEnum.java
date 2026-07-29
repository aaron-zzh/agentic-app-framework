package com.xuejiai.aaf.common.enums.content;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内容项目类型。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@AllArgsConstructor
public enum ContentProjectTypeEnum implements ArrayValuable<String> {
    NEW_PRODUCT("new_product", "新品推广"),
    PROMOTION("promotion", "活动促销"),
    BRAND_VISUAL("brand_visual", "品牌视觉"),
    STORE("store", "门店宣传"),
    SOCIAL("social", "社媒内容"),
    PERSONAL_IP("personal_ip", "个人 IP 内容"),
    NARRATIVE_SERIES("narrative_series", "系列叙事内容");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ContentProjectTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
