package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.FocusExtractionContextAssembler.FocusContext;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.IncrementalEntityResolver;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.IncrementalEntityResolver.EntityMention;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeAiMeter;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.FactAssertion;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.RunContext;
import com.xuejiai.aaf.framework.intelligent.ai.chat.DynamicChatClientFactory;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

/** 从焦点块抽取可验证事实并写入 PostgreSQL 真理表。 */
@Service
@RequiredArgsConstructor
public class EntityExtractionService {

    private final DynamicChatClientFactory chatClientFactory;
    private final KnowledgeAiMeter meter;
    private final IncrementalEntityResolver entityResolver;
    private final TrustedKnowledgeStore store;

    public int extractAndPersist(RunContext run, FocusContext context, Runnable executionGuard) {
        if (!EntityExtractionPrompt.OUTPUT_CONTRACT_VERSION.equals(
                run.extractionOutputContractVersion())) {
            throw new IllegalStateException(
                    "不支持的事实抽取输出契约: " + run.extractionOutputContractVersion());
        }
        var prompt =
                EntityExtractionPrompt.USER_PROMPT_TEMPLATE
                        .replace("{previous}", join(context.previousChunks()))
                        .replace("{focus}", context.focusContent())
                        .replace("{next}", join(context.nextChunks()));
        var unitKey = "chunk:" + context.focusChunkId();
        var meteredInput =
                run.extractionPromptDigest()
                        + "|"
                        + run.extractionOutputContractVersion()
                        + "|"
                        + prompt;
        var content =
                meter.invokeText(
                        run.billing(),
                        "knowledge-extraction",
                        unitKey,
                        run.extractionModelId(),
                        meteredInput,
                        () ->
                                chatClientFactory
                                        .get(run.extractionModelId())
                                        .prompt()
                                        .system(run.extractionSystemPrompt())
                                        .user(prompt)
                                        .call()
                                        .chatResponse());
        var triples = parseAndValidate(content, context.focusContent());
        var persisted = 0;
        for (var triple : triples) {
            executionGuard.run();
            var assertion =
                    new FactAssertion(triple.validAt(), triple.invalidAt(), triple.attributes());
            var subject =
                    entityResolver.resolve(
                            run,
                            new EntityMention(
                                    triple.subject(),
                                    triple.subjectType(),
                                    triple.subjectDescription()));
            if ("LITERAL".equals(triple.objectKind())) {
                store.persistLiteralFact(
                        run,
                        subject,
                        triple.predicate(),
                        triple.object(),
                        triple.confidence(),
                        context.focusChunkId(),
                        triple.evidenceQuote(),
                        triple.startOffset(),
                        triple.endOffset(),
                        context.contextChunkIds(),
                        assertion);
            } else {
                var object =
                        entityResolver.resolve(
                                run,
                                new EntityMention(
                                        triple.object(),
                                        triple.objectType(),
                                        triple.objectDescription()));
                store.persistFact(
                        run,
                        subject,
                        triple.predicate(),
                        object,
                        triple.confidence(),
                        context.focusChunkId(),
                        triple.evidenceQuote(),
                        triple.startOffset(),
                        triple.endOffset(),
                        context.contextChunkIds(),
                        assertion);
            }
            persisted++;
        }
        return persisted;
    }

    private List<ExtractedTriple> parseAndValidate(String content, String focus) {
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("事实抽取结果为空");
        }
        if (content.length() > EntityExtractionPrompt.MAX_RESPONSE_LENGTH) {
            throw new IllegalStateException("事实抽取结果超过最大长度");
        }
        final JsonNode root;
        try {
            root = JsonUtils.readTree(content);
        } catch (Exception failure) {
            throw new IllegalStateException("事实抽取结果不是有效 JSON", failure);
        }
        if (root == null || !root.isArray()) {
            throw new IllegalArgumentException("事实抽取结果顶层必须是 JSON 数组");
        }
        if (root.size() > EntityExtractionPrompt.MAX_FACTS_PER_CHUNK) {
            throw new IllegalArgumentException(
                    "单个焦点块最多抽取 " + EntityExtractionPrompt.MAX_FACTS_PER_CHUNK + " 条事实");
        }

        var triples = new ArrayList<ExtractedTriple>(root.size());
        var index = 0;
        for (var item : root) {
            requireExactFields(item, index);
            var triple =
                    new ExtractedTriple(
                            requiredString(item, "subject", index),
                            requiredString(item, "subjectType", index),
                            requiredString(item, "subjectDesc", index),
                            requiredString(item, "predicate", index),
                            requiredString(item, "object", index),
                            requiredString(item, "objectKind", index),
                            requiredString(item, "objectType", index),
                            requiredString(item, "objectDesc", index),
                            requiredString(item, "evidenceQuote", index),
                            requiredInteger(item, "startOffset", index),
                            requiredInteger(item, "endOffset", index),
                            requiredNumber(item, "confidence", index),
                            optionalInstant(item, "validAt", index),
                            optionalInstant(item, "invalidAt", index),
                            optionalAttributes(item, index));
            validateContract(triple, index);
            validateFocusEvidence(focus, triple);
            triples.add(triple);
            index++;
        }
        return List.copyOf(triples);
    }

    private void requireExactFields(JsonNode item, int index) {
        if (item == null || !item.isObject()) {
            throw invalidItem(index, "必须是 JSON 对象");
        }
        var actualFields = new LinkedHashSet<String>();
        item.properties().forEach(property -> actualFields.add(property.getKey()));
        var missing = new LinkedHashSet<>(EntityExtractionPrompt.REQUIRED_FIELDS);
        missing.removeAll(actualFields);
        var supported = new LinkedHashSet<>(EntityExtractionPrompt.REQUIRED_FIELDS);
        supported.addAll(EntityExtractionPrompt.OPTIONAL_FIELDS);
        var unknown = new LinkedHashSet<>(actualFields);
        unknown.removeAll(supported);
        if (!missing.isEmpty() || !unknown.isEmpty()) {
            throw invalidItem(index, "字段集合不匹配，缺失=" + missing + "，未知=" + unknown);
        }
    }

    private String requiredString(JsonNode item, String field, int index) {
        var value = item.get(field);
        if (value == null || !value.isTextual()) {
            throw invalidItem(index, field + " 必须是字符串");
        }
        return value.asString();
    }

    private int requiredInteger(JsonNode item, String field, int index) {
        var value = item.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalidItem(index, field + " 必须是 32 位整数");
        }
        return value.asInt();
    }

    private double requiredNumber(JsonNode item, String field, int index) {
        var value = item.get(field);
        if (value == null || !value.isNumber()) {
            throw invalidItem(index, field + " 必须是数值");
        }
        return value.asDouble();
    }

    private Instant optionalInstant(JsonNode item, String field, int index) {
        var value = item.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalidItem(index, field + " 必须是 ISO-8601 时间字符串或 null");
        }
        try {
            return Instant.parse(value.asString());
        } catch (DateTimeParseException failure) {
            throw invalidItem(index, field + " 必须是 ISO-8601 时间字符串");
        }
    }

    private Map<String, Object> optionalAttributes(JsonNode item, int index) {
        var value = item.get("attributes");
        if (value == null || value.isNull()) {
            return Map.of();
        }
        if (!value.isObject()) {
            throw invalidItem(index, "attributes 必须是 JSON 对象或 null");
        }
        if (value.size() > EntityExtractionPrompt.MAX_ATTRIBUTE_COUNT) {
            throw invalidItem(index, "attributes 键数量超过上限");
        }
        if (value.toString().getBytes(StandardCharsets.UTF_8).length
                > EntityExtractionPrompt.MAX_ATTRIBUTES_LENGTH) {
            throw invalidItem(index, "attributes 超过最大长度");
        }
        value.properties()
                .forEach(
                        property -> {
                            var key = property.getKey();
                            if (!key.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) {
                                throw invalidItem(index, "attributes 包含非法键: " + key);
                            }
                            if (EntityExtractionPrompt.RESERVED_ATTRIBUTE_KEYS.contains(key)) {
                                throw invalidItem(index, "attributes 包含保留键: " + key);
                            }
                            validateAttributeValue(property.getValue(), index, key);
                        });
        return Map.copyOf(
                JsonUtils.convertValue(value, new TypeReference<Map<String, Object>>() {}));
    }

    private void validateAttributeValue(JsonNode value, int index, String key) {
        if (value == null || value.isNull()) {
            throw invalidItem(index, "attributes 值不能为 null: " + key);
        }
        if (value.isTextual() || value.isBoolean() || value.isNumber()) {
            if (value.isNumber() && !Double.isFinite(value.asDouble())) {
                throw invalidItem(index, "attributes 数值必须有限: " + key);
            }
            return;
        }
        if (value.isArray()) {
            for (var item : value) {
                if (item != null
                        && !item.isNull()
                        && (item.isTextual()
                                || item.isBoolean()
                                || (item.isNumber() && Double.isFinite(item.asDouble())))) {
                    continue;
                }
                throw invalidItem(index, "attributes 数组只能包含非空标量: " + key);
            }
            return;
        }
        throw invalidItem(index, "attributes 不支持嵌套对象或数组: " + key);
    }

    private void validateContract(ExtractedTriple triple, int index) {
        validateLength(triple.subject(), "subject", EntityExtractionPrompt.MAX_NAME_LENGTH, index);
        validateLength(
                triple.subjectDescription(),
                "subjectDescription",
                EntityExtractionPrompt.MAX_DESCRIPTION_LENGTH,
                index);
        validateLength(
                triple.predicate(), "predicate", EntityExtractionPrompt.MAX_NAME_LENGTH, index);
        validateLength(triple.object(), "object", EntityExtractionPrompt.MAX_NAME_LENGTH, index);
        validateLength(
                triple.objectDescription(),
                "objectDescription",
                EntityExtractionPrompt.MAX_DESCRIPTION_LENGTH,
                index);
        validateLength(
                triple.evidenceQuote(),
                "evidenceQuote",
                EntityExtractionPrompt.MAX_EVIDENCE_LENGTH,
                index);

        if (!EntityExtractionPrompt.ENTITY_TYPES.contains(triple.subjectType())) {
            throw invalidItem(index, "subjectType 不在受控枚举中");
        }
        if (!"ENTITY".equals(triple.objectKind()) && !"LITERAL".equals(triple.objectKind())) {
            throw invalidItem(index, "objectKind 仅支持 ENTITY 或 LITERAL");
        }
        if ("ENTITY".equals(triple.objectKind())) {
            if (!EntityExtractionPrompt.ENTITY_TYPES.contains(triple.objectType())) {
                throw invalidItem(index, "ENTITY 的 objectType 不在受控枚举中");
            }
        } else if (!triple.objectType().isEmpty() || !triple.objectDescription().isEmpty()) {
            throw invalidItem(index, "LITERAL 的 objectType 和 objectDescription 必须为空字符串");
        }
        if (triple.validAt() != null
                && triple.invalidAt() != null
                && !triple.validAt().isBefore(triple.invalidAt())) {
            throw invalidItem(index, "validAt 必须早于 invalidAt");
        }
    }

    private void validateLength(String value, String field, int maxLength, int index) {
        if (value.length() > maxLength) {
            throw invalidItem(index, field + " 超过最大长度 " + maxLength);
        }
    }

    private IllegalArgumentException invalidItem(int index, String message) {
        return new IllegalArgumentException("事实抽取结果第 " + index + " 项无效: " + message);
    }

    void validateFocusEvidence(String focus, ExtractedTriple triple) {
        if (triple.evidenceQuote() == null || triple.evidenceQuote().isEmpty()) {
            throw new IllegalArgumentException("事实缺少焦点证据");
        }
        if (triple.startOffset() == null
                || triple.endOffset() == null
                || triple.startOffset() < 0
                || triple.endOffset() <= triple.startOffset()
                || triple.endOffset() > focus.length()) {
            throw new IllegalArgumentException("事实证据字符区间越界");
        }
        var actual = focus.substring(triple.startOffset(), triple.endOffset());
        if (!actual.equals(triple.evidenceQuote())) {
            throw new IllegalArgumentException("事实证据必须逐字来自焦点块");
        }
        if (TrustedKnowledgeStore.normalize(triple.subject()).isBlank()
                || TrustedKnowledgeStore.normalize(triple.predicate()).isBlank()
                || TrustedKnowledgeStore.normalize(triple.object()).isBlank()) {
            throw new IllegalArgumentException("事实规范化后不能为空");
        }
        if (triple.confidence() == null
                || !Double.isFinite(triple.confidence())
                || triple.confidence() < 0
                || triple.confidence() > 1) {
            throw new IllegalArgumentException("事实置信度必须是 0 到 1 之间的有限数");
        }
    }

    private String join(
            List<
                            com.xuejiai.aaf.framework.engine.knowledge.trusted
                                    .FocusExtractionContextAssembler.ContextChunk>
                    chunks) {
        return chunks.stream()
                .map(
                        chunk ->
                                "[chunkId=%s, contextOnly=true, truncated=%s]\n%s"
                                        .formatted(
                                                chunk.chunkId(),
                                                chunk.truncated(),
                                                chunk.content()))
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
