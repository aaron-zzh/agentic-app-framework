package com.xuejiai.aaf.common.enums.profile;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 用户生命周期阶段枚举。 */
@Getter
@AllArgsConstructor
public enum LifecycleStageEnum implements ArrayValuable<String> {
    NEW("new", "新用户"),
    ACTIVE("active", "活跃"),
    DORMANT("dormant", "沉默"),
    CHURNED("churned", "流失");

    private final String code;
    private final String label;

    @Override
    public String[] array() {
        return java.util.Arrays.stream(values())
                .map(LifecycleStageEnum::getCode)
                .toArray(String[]::new);
    }
}
