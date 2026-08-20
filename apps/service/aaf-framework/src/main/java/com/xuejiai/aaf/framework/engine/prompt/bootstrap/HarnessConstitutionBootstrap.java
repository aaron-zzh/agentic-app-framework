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

/** 应用 Ready 前严格注册唯一的 AAF Harness Constitution。 */
@Component
public final class HarnessConstitutionBootstrap implements ApplicationRunner, Ordered {

    private static final String RESOURCE_PATH = "aaf/prompt/harness-constitution.md";
    private static final String PROMPT_NAME = "aaf.harness.constitution";
    private static final Set<String> REQUIRED_FIELDS =
            Set.of("aaf-prompt-format", "name", "template-version", "sha256", "category");

    private final PromptEngine promptEngine;

    public HarnessConstitutionBootstrap(PromptEngine promptEngine) {
        this.promptEngine = promptEngine;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
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

    private static ConstitutionResource parseResource() {
        var resource = new ClassPathResource(RESOURCE_PATH);
        if (!resource.exists()) {
            throw new IllegalStateException("Harness Constitution 资源缺失: " + RESOURCE_PATH);
        }
        final byte[] bytes;
        try (var input = resource.getInputStream()) {
            bytes = input.readAllBytes();
        } catch (IOException failure) {
            throw new IllegalStateException("Harness Constitution 资源不可读", failure);
        }
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF) {
            throw new IllegalStateException("Harness Constitution 禁止 UTF-8 BOM");
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
            throw new IllegalStateException("Harness Constitution 不是合法 UTF-8", failure);
        }
        var canonical = raw.replace("\r\n", "\n").replace('\r', '\n');
        if (!canonical.startsWith("---\n")) {
            throw new IllegalStateException("Harness Constitution 缺少起始 frontmatter 分隔符");
        }
        var end = canonical.indexOf("\n---\n", 4);
        if (end < 0) {
            throw new IllegalStateException("Harness Constitution 缺少结束 frontmatter 分隔符");
        }
        var fields = parseFields(canonical.substring(4, end));
        if (!fields.keySet().equals(REQUIRED_FIELDS)) {
            throw new IllegalStateException("Harness Constitution manifest 字段必须精确匹配规范");
        }
        if (!"1".equals(fields.get("aaf-prompt-format"))) {
            throw new IllegalStateException("不支持的 aaf-prompt-format");
        }
        if (!PROMPT_NAME.equals(fields.get("name"))) {
            throw new IllegalStateException("Harness Constitution name 非法");
        }
        final int templateVersion;
        try {
            templateVersion = Integer.parseInt(fields.get("template-version"));
        } catch (NumberFormatException failure) {
            throw new IllegalStateException("Harness Constitution template-version 非法", failure);
        }
        if (templateVersion < 1) {
            throw new IllegalStateException("Harness Constitution template-version 必须大于 0");
        }
        var expectedSha256 = fields.get("sha256");
        if (!expectedSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("Harness Constitution sha256 必须是 64 位小写十六进制");
        }
        if (!"HARNESS".equals(fields.get("category"))) {
            throw new IllegalStateException("Harness Constitution category 必须是 HARNESS");
        }
        var body = canonical.substring(end + "\n---\n".length());
        if (body.isBlank()) {
            throw new IllegalStateException("Harness Constitution 正文不能为空");
        }
        if (body.contains("${")) {
            throw new IllegalStateException("Harness Constitution 禁止模板变量");
        }
        if (!body.endsWith("\n") || body.endsWith("\n\n")) {
            throw new IllegalStateException("Harness Constitution 正文必须恰好以一个换行结尾");
        }
        var actualSha256 = EnginePromptRegistration.sha256(body);
        if (!actualSha256.equals(expectedSha256)) {
            throw new IllegalStateException("Harness Constitution 正文摘要不匹配");
        }
        return new ConstitutionResource(
                PROMPT_NAME, templateVersion, expectedSha256, fields.get("category"), body);
    }

    private static Map<String, String> parseFields(String frontmatter) {
        var fields = new LinkedHashMap<String, String>();
        for (var line : frontmatter.split("\n", -1)) {
            var separator = line.indexOf(':');
            if (separator <= 0 || separator == line.length() - 1) {
                throw new IllegalStateException("Harness Constitution manifest 行非法: " + line);
            }
            var key = line.substring(0, separator).trim();
            var value = line.substring(separator + 1).trim();
            if (!REQUIRED_FIELDS.contains(key)
                    || value.isEmpty()
                    || fields.put(key, value) != null) {
                throw new IllegalStateException("Harness Constitution manifest 字段非法: " + key);
            }
        }
        return Map.copyOf(fields);
    }

    private record ConstitutionResource(
            String name, int templateVersion, String sha256, String category, String body) {}
}
