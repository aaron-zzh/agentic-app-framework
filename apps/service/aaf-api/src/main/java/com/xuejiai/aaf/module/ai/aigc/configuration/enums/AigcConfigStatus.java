package com.xuejiai.aaf.module.ai.aigc.configuration.enums;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** AIGC 配置发布状态。 */
@Getter
@AllArgsConstructor
public enum AigcConfigStatus implements ArrayValuable<String> {
    DRAFT("draft", "草稿"),
    VERIFYING("verifying", "验证"),
    PUBLISHED("published", "已发布"),
    DEPRECATED("deprecated", "已弃用"),
    WITHDRAWN("withdrawn", "已撤回");

    private final String code;
    private final String label;

    private static final String[] VALUES =
            Arrays.stream(values()).map(AigcConfigStatus::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return VALUES;
    }
}
