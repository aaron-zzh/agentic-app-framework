package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DocTypeEnum implements ArrayValuable<String> {
    SPEC("spec", "规范文档"),
    GUIDE("guide", "指南"),
    EXPLANATION("explanation", "说明"),
    TUTORIAL("tutorial", "教程"),
    AIGC_SCRIPT("aigc_script", "AIGC 脚本"),
    AIGC_POST("aigc_post", "AIGC 文案");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(DocTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
