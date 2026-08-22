package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** L3 编译并冻结、供 Harness 唯一消费的最终 System Prompt。 */
public record CompiledSystemPrompt(
        String compilerVersion, List<PromptLayerSnapshot> layers, String content, String sha256) {

    public static final String COMPILER_VERSION = "aaf-prompt-v3";
    public static final String CONSTITUTION_NAME = "aaf.harness.constitution";

    public CompiledSystemPrompt {
        compilerVersion = requireMetadata(compilerVersion, "compilerVersion");
        layers = List.copyOf(Objects.requireNonNull(layers, "layers 不能为空"));
        content = requireText(content, "content");
        sha256 = requireMetadata(sha256, "sha256");
        verifyValues(compilerVersion, layers, content, sha256);
    }

    public static CompiledSystemPrompt compile(List<PromptLayerSource> sources) {
        Objects.requireNonNull(sources, "sources 不能为空");
        var snapshots = new ArrayList<PromptLayerSnapshot>(sources.size());
        for (var ordinal = 0; ordinal < sources.size(); ordinal++) {
            var source = Objects.requireNonNull(sources.get(ordinal), "Prompt layer 不能为空");
            var layerContent = canonicalLayerContent(source.content());
            snapshots.add(
                    new PromptLayerSnapshot(
                            ordinal,
                            source.kind(),
                            source.sourceKind(),
                            source.sourceKey(),
                            source.sourceVersion(),
                            layerContent,
                            sha256(layerContent)));
        }
        var frozenLayers = List.copyOf(snapshots);
        var finalContent = render(frozenLayers);
        return new CompiledSystemPrompt(
                COMPILER_VERSION, frozenLayers, finalContent, sha256(finalContent));
    }

    public void verify() {
        verifyValues(compilerVersion, layers, content, sha256);
    }

    public void requireCompatible(SubagentSpec spec) {
        Objects.requireNonNull(spec, "subagentSpec 不能为空");
        switch (spec) {
            case SubagentSpec.Predefined predefined ->
                    requireSource(
                            PromptSourceKind.AGENT_DEFINITION,
                            predefined.agentId().value(),
                            Long.toString(predefined.version()));
            case SubagentSpec.Dynamic dynamic -> {
                requireSource(PromptSourceKind.AAF_POLICY, dynamic.identifier(), "1");
                var personaCount =
                        layers.stream()
                                .filter(
                                        layer ->
                                                layer.sourceKind()
                                                        == PromptSourceKind.ASSISTANT_PERSONA)
                                .count();
                if (personaCount > 1) {
                    throw new IllegalStateException("动态 Harness Prompt 最多只能包含一个 Assistant Persona 层");
                }
            }
        }
    }

    private void requireSource(
            PromptSourceKind sourceKind, String sourceKey, String sourceVersion) {
        var matches =
                layers.stream()
                        .filter(layer -> layer.sourceKind() == sourceKind)
                        .filter(layer -> layer.sourceKey().equals(sourceKey))
                        .filter(layer -> layer.sourceVersion().equals(sourceVersion))
                        .count();
        if (matches != 1) {
            throw new IllegalStateException(
                    "冻结 Prompt 与执行目标不匹配: %s/%s@%s".formatted(sourceKind, sourceKey, sourceVersion));
        }
    }

    private static void verifyValues(
            String compilerVersion,
            List<PromptLayerSnapshot> layers,
            String content,
            String sha256) {
        if (!COMPILER_VERSION.equals(compilerVersion)) {
            throw new IllegalStateException("不支持的 Prompt compilerVersion: " + compilerVersion);
        }
        if (layers.isEmpty()) {
            throw new IllegalStateException("冻结 Prompt 不允许为空");
        }
        var sources = new HashSet<String>();
        var constitutionCount = 0;
        var invocationPolicyCount = 0;
        var previousKind = -1;
        for (var ordinal = 0; ordinal < layers.size(); ordinal++) {
            var layer = Objects.requireNonNull(layers.get(ordinal), "Prompt layer 不能为空");
            if (layer.ordinal() != ordinal) {
                throw new IllegalStateException("Prompt layer ordinal 必须从 0 连续递增");
            }
            var kindOrder = kindOrder(layer.kind());
            if (kindOrder < previousKind) {
                throw new IllegalStateException("Prompt layer 类型顺序非法");
            }
            previousKind = kindOrder;
            if (!canonicalLayerContent(layer.content()).equals(layer.content())) {
                throw new IllegalStateException("Prompt layer content 不是 canonical 形式");
            }
            if (!sha256(layer.content()).equals(layer.sourceSha256())) {
                throw new IllegalStateException("Prompt layer 摘要不匹配: " + layer.sourceKey());
            }
            var sourceIdentity =
                    "%s|%s|%s"
                            .formatted(
                                    layer.sourceKind(), layer.sourceKey(), layer.sourceVersion());
            if (!sources.add(sourceIdentity)) {
                throw new IllegalStateException("Prompt layer 来源重复: " + sourceIdentity);
            }
            if (layer.kind() == PromptLayerKind.CONSTITUTION) {
                constitutionCount++;
                if (layer.sourceKind() != PromptSourceKind.ENGINE_TEMPLATE
                        || !CONSTITUTION_NAME.equals(layer.sourceKey())) {
                    throw new IllegalStateException("Constitution 必须来自指定 ENGINE_TEMPLATE");
                }
            }
            if (layer.kind() == PromptLayerKind.INVOCATION_POLICY) {
                invocationPolicyCount++;
            }
        }
        if (constitutionCount != 1 || layers.getFirst().kind() != PromptLayerKind.CONSTITUTION) {
            throw new IllegalStateException("冻结 Prompt 必须以唯一 Constitution 开始");
        }
        if (invocationPolicyCount != 1) {
            throw new IllegalStateException("冻结 Prompt 必须包含唯一身份职责与交付契约层");
        }
        if (!render(layers).equals(content)) {
            throw new IllegalStateException("冻结 Prompt content 与 layers 不一致");
        }
        if (!sha256(content).equals(sha256)) {
            throw new IllegalStateException("冻结 Prompt 最终摘要不匹配");
        }
    }

    private static int kindOrder(PromptLayerKind kind) {
        return switch (kind) {
            case CONSTITUTION -> 0;
            case IDENTITY -> 1;
            case INVOCATION_POLICY -> 2;
            case ROLE -> 3;
            case SKILL -> 4;
            case PERSONA -> 5;
        };
    }

    private static String render(List<PromptLayerSnapshot> layers) {
        var result =
                new StringBuilder(
                        "<AAF_SYSTEM_PROMPT compiler=\"%s\">\n".formatted(COMPILER_VERSION));
        for (var layer : layers) {
            result.append(
                    "[AAF_LAYER ordinal=%d kind=%s source=%s key=%s version=%s sha256=%s]\n"
                            .formatted(
                                    layer.ordinal(),
                                    layer.kind(),
                                    layer.sourceKind(),
                                    layer.sourceKey(),
                                    layer.sourceVersion(),
                                    layer.sourceSha256()));
            result.append(layer.content());
            result.append("[/AAF_LAYER]\n");
        }
        return result.append("</AAF_SYSTEM_PROMPT>\n").toString();
    }

    private static String canonicalLayerContent(String value) {
        var normalized =
                requireText(value, "layer content").replace("\r\n", "\n").replace('\r', '\n');
        rejectReservedSyntax(normalized);
        var end = normalized.length();
        while (end > 0 && normalized.charAt(end - 1) == '\n') {
            end--;
        }
        return normalized.substring(0, end) + "\n";
    }

    private static void rejectReservedSyntax(String content) {
        if (content.contains("<AAF_SYSTEM_PROMPT")
                || content.contains("</AAF_SYSTEM_PROMPT>")
                || content.contains("[AAF_LAYER")
                || content.contains("[/AAF_LAYER]")) {
            throw new IllegalArgumentException("Prompt layer content 包含 AAF 保留封装语法");
        }
    }

    private static String sha256(String value) {
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

    private static String requireMetadata(String value, String field) {
        value = requireText(value, field);
        if (value.length() > 256 || !value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}")) {
            throw new IllegalArgumentException(field + " 不是合法的稳定元数据");
        }
        return value;
    }

    public record PromptLayerSource(
            PromptLayerKind kind,
            PromptSourceKind sourceKind,
            String sourceKey,
            String sourceVersion,
            String content) {

        public PromptLayerSource {
            Objects.requireNonNull(kind, "kind 不能为空");
            Objects.requireNonNull(sourceKind, "sourceKind 不能为空");
            sourceKey = requireMetadata(sourceKey, "sourceKey");
            sourceVersion = requireMetadata(sourceVersion, "sourceVersion");
            content = requireText(content, "content");
        }
    }

    public record PromptLayerSnapshot(
            int ordinal,
            PromptLayerKind kind,
            PromptSourceKind sourceKind,
            String sourceKey,
            String sourceVersion,
            String content,
            String sourceSha256) {

        public PromptLayerSnapshot {
            if (ordinal < 0) {
                throw new IllegalArgumentException("ordinal 不能小于 0");
            }
            Objects.requireNonNull(kind, "kind 不能为空");
            Objects.requireNonNull(sourceKind, "sourceKind 不能为空");
            sourceKey = requireMetadata(sourceKey, "sourceKey");
            sourceVersion = requireMetadata(sourceVersion, "sourceVersion");
            content = requireText(content, "content");
            sourceSha256 = requireMetadata(sourceSha256, "sourceSha256");
        }
    }

    /**
     * System Prompt 的层类型，声明顺序即优先级顺序。
     *
     * <p>排序原则是「不可协商程度」降序：跨身份治理 → 冻结身份与身份职责合同 → 交付与完成契约 → Role 边界 → 领域方法 → 表达风格。冲突时靠前者胜出，
     * 因此表达风格必须排在最后——它只能改变怎么说，不能改变做什么。
     */
    public enum PromptLayerKind {
        CONSTITUTION,
        IDENTITY,
        INVOCATION_POLICY,
        ROLE,
        SKILL,
        PERSONA
    }

    public enum PromptSourceKind {
        ENGINE_TEMPLATE,
        AGENT_DEFINITION,
        ASSISTANT_PERSONA,
        ASSISTANT_ROLE,
        SKILL_VERSION,
        AAF_POLICY
    }
}
