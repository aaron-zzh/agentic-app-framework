package com.xuejiai.aaf.common.enums.profile;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 画像维度分组枚举。 */
@Getter
@AllArgsConstructor
public enum DimensionGroupEnum implements ArrayValuable<String> {
    BASIC("basic", "基础信息"),
    PREFERENCE("preference", "偏好"),
    BEHAVIOR("behavior", "行为"),
    HEALTH("health", "健康"),
    LIVING("living", "生活"),
    SHOPPING("shopping", "消费"),
    SOCIAL("social", "社交"),
    PERSONALITY("personality", "性格");

    private final String code;
    private final String label;

    @Override
    public String[] array() {
        return java.util.Arrays.stream(values()).map(DimensionGroupEnum::getCode).toArray(String[]::new);
    }
}
