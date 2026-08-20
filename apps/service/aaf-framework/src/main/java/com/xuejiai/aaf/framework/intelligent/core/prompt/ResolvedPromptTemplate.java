package com.xuejiai.aaf.framework.intelligent.core.prompt;

import java.util.Objects;

/** 从 PromptEngine 解析出的不可变精确模板。 */
public record ResolvedPromptTemplate(String name, int version, String content, String sha256) {

    public ResolvedPromptTemplate {
        name = requireText(name, "name");
        if (version < 1) {
            throw new IllegalArgumentException("version 必须大于 0");
        }
        content = requireText(content, "content");
        sha256 = requireText(sha256, "sha256");
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 必须是 64 位小写十六进制");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value;
    }
}
