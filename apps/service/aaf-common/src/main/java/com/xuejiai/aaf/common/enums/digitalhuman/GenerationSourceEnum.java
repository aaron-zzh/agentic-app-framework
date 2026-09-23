package com.xuejiai.aaf.common.enums.digitalhuman;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 数字人生成来源枚举，对应字典 digital_human_generation_source */
@Getter
@AllArgsConstructor
public enum GenerationSourceEnum implements ArrayValuable<String> {
    MANUAL("manual", "手动创建"),
    AI_GENERATED("ai_generated", "AI 生成"),
    IMPORTED("imported", "导入"),
    TEMPLATE("template", "模板克隆");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(GenerationSourceEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
