package com.xuejiai.aaf.framework.engine.prompt.bootstrap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.prompt.EnginePromptRegistration;
import com.xuejiai.aaf.framework.engine.prompt.PromptEngine;

/** 启动时校验并不可变注册 AAF 上下文摘要 ENGINE Prompt。 */
@Component
public final class ContextSummaryPromptBootstrap implements ApplicationRunner, Ordered {

    private static final String RESOURCE_PATH = "aaf/prompt/context-summary.md";
    private static final String PROMPT_NAME = "aaf.context.summary";
    private static final Set<String> REQUIRED_FIELDS =
            Set.of("aaf-prompt-format", "name", "template-version", "sha256", "category");

    private final PromptEngine promptEngine;

    public ContextSummaryPromptBootstrap(PromptEngine promptEngine) {
        this.promptEngine = promptEngine;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public void run(ApplicationArguments args) {
        var resource = parseResource();
        promptEngine.registerEngineVersion(
                new EnginePromptRegistration(
                        resource.name(),
                        resource.templateVersion(),
                        resource.body(),
                        resource.sha256(),
                        resource.category(),
                        true));
    }

    private static PromptResource parseResource() {
        var resource = new ClassPathResource(RESOURCE_PATH);
        if (!resource.exists()) {
            throw new IllegalStateException("上下文摘要 Prompt 资源缺失: " + RESOURCE_PATH);
        }
        final byte[] bytes;
        try (var input = resource.getInputStream()) {
            bytes = input.readAllBytes();
        } catch (IOException failure) {
            throw new IllegalStateException("上下文摘要 Prompt 资源不可读", failure);
        }
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF) {
            throw new IllegalStateException("上下文摘要 Prompt 禁止 UTF-8 BOM");
        }
        var decoder =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
        final String raw;
        try {
            raw = decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException failure) {
            throw new IllegalStateException("上下文摘要 Prompt 不是合法 UTF-8", failure);
        }
        var canonical = raw.replace("\r\n", "\n").replace('\r', '\n');
        if (!canonical.startsWith("---\n")) {
            throw new IllegalStateException("上下文摘要 Prompt 缺少起始 frontmatter 分隔符");
        }
        var end = canonical.indexOf("\n---\n", 4);
        if (end < 0) {
            throw new IllegalStateException("上下文摘要 Prompt 缺少结束 frontmatter 分隔符");
        }
        var fields = parseFields(canonical.substring(4, end));
        if (!fields.keySet().equals(REQUIRED_FIELDS)) {
            throw new IllegalStateException("上下文摘要 Prompt manifest 字段必须精确匹配规范");
        }
        if (!"1".equals(fields.get("aaf-prompt-format"))) {
            throw new IllegalStateException("不支持的 aaf-prompt-format");
        }
        if (!PROMPT_NAME.equals(fields.get("name"))) {
            throw new IllegalStateException("上下文摘要 Prompt name 非法");
        }
        final int templateVersion;
        try {
            templateVersion = Integer.parseInt(fields.get("template-version"));
        } catch (NumberFormatException failure) {
            throw new IllegalStateException("上下文摘要 Prompt template-version 非法", failure);
        }
        if (templateVersion < 1) {
            throw new IllegalStateException("上下文摘要 Prompt template-version 必须大于 0");
        }
        var expectedSha256 = fields.get("sha256");
        if (!expectedSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("上下文摘要 Prompt sha256 必须是 64 位小写十六进制");
        }
        if (!"CONTEXT".equals(fields.get("category"))) {
            throw new IllegalStateException("上下文摘要 Prompt category 必须是 CONTEXT");
        }
        var body = canonical.substring(end + "\n---\n".length());
        if (body.isBlank() || body.contains("${")) {
            throw new IllegalStateException("上下文摘要 Prompt 正文不能为空且禁止运行时变量");
        }
        if (!body.endsWith("\n") || body.endsWith("\n\n")) {
            throw new IllegalStateException("上下文摘要 Prompt 正文必须恰好以一个换行结尾");
        }
        var actualSha256 = EnginePromptRegistration.sha256(body);
        if (!actualSha256.equals(expectedSha256)) {
            throw new IllegalStateException("上下文摘要 Prompt 正文摘要不匹配");
        }
        return new PromptResource(
                PROMPT_NAME, templateVersion, expectedSha256, fields.get("category"), body);
    }

    private static Map<String, String> parseFields(String frontmatter) {
        var fields = new LinkedHashMap<String, String>();
        for (var line : frontmatter.split("\n", -1)) {
            var separator = line.indexOf(':');
            if (separator <= 0 || separator == line.length() - 1) {
                throw new IllegalStateException("上下文摘要 Prompt manifest 行非法: " + line);
            }
            var key = line.substring(0, separator).trim();
            var value = line.substring(separator + 1).trim();
            if (!REQUIRED_FIELDS.contains(key)
                    || value.isEmpty()
                    || fields.put(key, value) != null) {
                throw new IllegalStateException("上下文摘要 Prompt manifest 字段非法: " + key);
            }
        }
        return Map.copyOf(fields);
    }

    private record PromptResource(
            String name, int templateVersion, String sha256, String category, String body) {}
}
