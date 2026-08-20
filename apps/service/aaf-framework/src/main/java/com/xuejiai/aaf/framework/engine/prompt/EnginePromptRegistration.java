package com.xuejiai.aaf.framework.engine.prompt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** classpath 内建 ENGINE Prompt 的精确版本注册请求。 */
public record EnginePromptRegistration(
        String name,
        int templateVersion,
        String content,
        String contentSha256,
        String category,
        boolean activate) {

    public EnginePromptRegistration {
        name = requireText(name, "name");
        if (templateVersion < 1) {
            throw new IllegalArgumentException("templateVersion 必须大于 0");
        }
        content = requireText(content, "content");
        contentSha256 = requireText(contentSha256, "contentSha256");
        if (!contentSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("contentSha256 必须是 64 位小写十六进制");
        }
        category = requireText(category, "category");
        var actual = sha256(content);
        if (!actual.equals(contentSha256)) {
            throw new IllegalArgumentException("ENGINE Prompt 内容摘要不匹配");
        }
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("运行环境不支持 SHA-256", failure);
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
