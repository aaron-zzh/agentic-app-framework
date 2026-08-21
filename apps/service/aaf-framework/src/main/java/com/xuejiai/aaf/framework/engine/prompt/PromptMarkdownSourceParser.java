package com.xuejiai.aaf.framework.engine.prompt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** 解析仅用于受治理同步的 Prompt Markdown 源文件。 */
@Component
public class PromptMarkdownSourceParser {

    public PromptSource parse(Resource resource) {
        try (var input = resource.getInputStream()) {
            var raw = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            if (raw.startsWith("\uFEFF"))
                throw new IllegalStateException("Prompt 源文件禁止 UTF-8 BOM: " + resource);
            var text = raw.replace("\r\n", "\n").replace('\r', '\n');
            if (!text.startsWith("---\n"))
                throw new IllegalStateException("Prompt 源文件缺少 frontmatter: " + resource);
            var end = text.indexOf("\n---\n", 4);
            if (end < 0)
                throw new IllegalStateException("Prompt 源文件缺少 frontmatter 结束符: " + resource);
            var fields = new LinkedHashMap<String, String>();
            for (var line : text.substring(4, end).split("\n")) {
                var separator = line.indexOf(':');
                if (separator <= 0
                        || fields.put(
                                        line.substring(0, separator).trim(),
                                        line.substring(separator + 1).trim())
                                != null) {
                    throw new IllegalStateException("Prompt frontmatter 字段非法: " + resource);
                }
            }
            if (!fields.keySet().equals(java.util.Set.of("code", "version", "changeSummary"))) {
                throw new IllegalStateException(
                        "Prompt frontmatter 仅允许 code、version、changeSummary: " + resource);
            }
            var code = fields.get("code");
            if (code == null || !code.matches("[a-z][a-z0-9.-]{2,127}"))
                throw new IllegalStateException("Prompt code 非法: " + resource);
            var version = Integer.parseInt(fields.get("version"));
            if (version < 1) throw new IllegalStateException("Prompt version 必须大于 0: " + resource);
            var content = text.substring(end + 5);
            if (content.isBlank()) throw new IllegalStateException("Prompt 内容不能为空: " + resource);
            return new PromptSource(
                    code,
                    version,
                    content,
                    fields.get("changeSummary"),
                    sha256(content),
                    resource.getDescription());
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException stateException) throw stateException;
            throw new IllegalStateException("Prompt 源文件不可读: " + resource, exception);
        }
    }

    private static String sha256(String content) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    public record PromptSource(
            String code,
            int version,
            String content,
            String changeSummary,
            String contentHash,
            String source) {}
}
