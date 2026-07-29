package com.xuejiai.aaf.common.enums.content;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内容配置状态。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@AllArgsConstructor
public enum ContentConfigStatusEnum implements ArrayValuable<String> {
    DRAFT("draft", "草稿"),
    VERIFYING("verifying", "验证"),
    PUBLISHED("published", "已发布"),
    DEPRECATED("deprecated", "已弃用"),
    WITHDRAWN("withdrawn", "已撤回");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ContentConfigStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
