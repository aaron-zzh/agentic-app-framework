package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.FocusExtractionContextAssembler.FocusContext;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.IncrementalEntityResolver;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.IncrementalEntityResolver.EntityMention;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeAiMeter;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.FactAssertion;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.RunContext;
import com.xuejiai.aaf.framework.engine.meta.runtime.NonRetryableTaskException;
import com.xuejiai.aaf.framework.intelligent.ai.chat.DynamicChatClientFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 从焦点块抽取可验证事实并写入 PostgreSQL 真理表。 */
@Slf4j
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
                run.extractionUserPrompt()
                        .replace("{previous}", join(context.previousChunks()))
                        .replace("{focus}", context.focusContent())
                        .replace("{next}", join(context.nextChunks()));
        var unitKey = "chunk:" + context.focusChunkId();
        var extractionSystemPrompt =
                run.extractionSystemPrompt() + "\n\n" + EntityExtractionPrompt.OUTPUT_INSTRUCTIONS;
        var meteredInput =
                run.extractionPromptDigest()
                        + "|"
                        + run.extractionOutputContractVersion()
                        + "|"
                        + EntityExtractionPrompt.OUTPUT_INSTRUCTIONS
                        + "|"
                        + prompt;
        var extractedTriples =
                meter.invokeEntityExtraction(
                        run.billing(),
                        "knowledge-extraction",
                        unitKey,
                        run.extractionModelId(),
                        meteredInput,
                        () ->
                                chatClientFactory
                                        .get(run.extractionModelId())
                                        .prompt()
                                        .system(extractionSystemPrompt)
                                        .user(prompt)
                                        .call()
                                        .responseEntity(
                                                new ParameterizedTypeReference<
                                                        List<ExtractedTriple>>() {}));
        if (log.isDebugEnabled()) {
            log.debug(
                    "[KnowledgeExtraction] 结构化返回: runId={}, chunkId={}, count={}, result={}",
                    run.runId(),
                    context.focusChunkId(),
                    extractedTriples == null ? null : extractedTriples.size(),
                    JsonUtils.toJsonString(extractedTriples));
        }
        var triples = validateAndPrepare(run, context, extractedTriples);

        var persisted = 0;
        for (var triple : triples) {
            executionGuard.run();
            try {
                var assertion =
                        new FactAssertion(
                                triple.validAt(), triple.invalidAt(), triple.attributes());
                var subject =
                        entityResolver.resolve(
                                run,
                                new EntityMention(
                                        triple.subject(),
                                        triple.subjectType().name(),
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
            } catch (IncrementalEntityResolver.InvalidEntityResolutionException failure) {
                log.warn(
                        "[KnowledgeExtraction] 丢弃实体消歧异常事实: runId={}, chunkId={}, subject={}, predicate={}, object={}, reason={}",
                        run.runId(),
                        context.focusChunkId(),
                        triple.subject(),
                        triple.predicate(),
                        triple.object(),
                        failure.getMessage());
            }
        }
        return persisted;
    }

    private List<ExtractedTriple> validateAndPrepare(
            RunContext run, FocusContext context, List<ExtractedTriple> triples) {
        if (triples == null) {
            throw new NonRetryableTaskException("事实抽取结构化结果为空");
        }
        var focus = context.focusContent();
        if (focus == null) {
            throw new NonRetryableTaskException("事实抽取焦点块为空");
        }
        if (triples.size() > EntityExtractionPrompt.MAX_FACTS_PER_CHUNK) {
            throw new NonRetryableTaskException(
                    "单个焦点块最多抽取 " + EntityExtractionPrompt.MAX_FACTS_PER_CHUNK + " 条事实");
        }
        var prepared = new ArrayList<ExtractedTriple>(triples.size());
        for (var index = 0; index < triples.size(); index++) {
            try {
                var corrected = correctEvidenceOffsets(focus, triples.get(index));
                validateContract(corrected, index);
                validateFocusEvidence(focus, corrected);
                prepared.add(corrected);
            } catch (NonRetryableTaskException failure) {
                log.warn(
                        "[KnowledgeExtraction] 丢弃无效事实: runId={}, chunkId={}, index={}, reason={}",
                        run.runId(),
                        context.focusChunkId(),
                        index,
                        failure.getMessage());
            }
        }
        return List.copyOf(prepared);
    }

    private ExtractedTriple correctEvidenceOffsets(String focus, ExtractedTriple triple) {
        if (triple == null) {
            return null;
        }
        var quote = triple.evidenceQuote();
        if (quote == null || quote.isEmpty()) {
            return triple;
        }
        var start = triple.startOffset();
        var end = triple.endOffset();
        if (start != null
                && end != null
                && start >= 0
                && end > start
                && end <= focus.length()
                && focus.substring(start, end).equals(quote)) {
            return triple;
        }
        var correctedStart = focus.indexOf(quote);
        if (correctedStart < 0) {
            return triple;
        }
        return new ExtractedTriple(
                triple.subject(),
                triple.subjectType(),
                triple.subjectDescription(),
                triple.predicate(),
                triple.object(),
                triple.objectKind(),
                triple.objectType(),
                triple.objectDescription(),
                quote,
                correctedStart,
                correctedStart + quote.length(),
                triple.confidence(),
                triple.validAt(),
                triple.invalidAt(),
                triple.attributes());
    }

    private void validateContract(ExtractedTriple triple, int index) {
        if (triple == null) {
            throw invalidItem(index, "不能为空");
        }
        validateString(triple.subject(), "subject", EntityExtractionPrompt.MAX_NAME_LENGTH, index);
        if (triple.subjectType() == null) {
            throw invalidItem(index, "subjectType 必须是受控枚举值");
        }
        validateString(
                triple.subjectDescription(),
                "subjectDescription",
                EntityExtractionPrompt.MAX_DESCRIPTION_LENGTH,
                index);
        validateString(
                triple.predicate(), "predicate", EntityExtractionPrompt.MAX_NAME_LENGTH, index);
        validateString(triple.object(), "object", EntityExtractionPrompt.MAX_NAME_LENGTH, index);
        validateString(
                triple.objectKind(), "objectKind", EntityExtractionPrompt.MAX_NAME_LENGTH, index);
        validateString(
                triple.objectType(), "objectType", EntityExtractionPrompt.MAX_NAME_LENGTH, index);
        validateString(
                triple.objectDescription(),
                "objectDescription",
                EntityExtractionPrompt.MAX_DESCRIPTION_LENGTH,
                index);
        validateString(
                triple.evidenceQuote(),
                "evidenceQuote",
                EntityExtractionPrompt.MAX_EVIDENCE_LENGTH,
                index);

        if (triple.startOffset() == null) {
            throw invalidItem(index, "startOffset 必须是 32 位整数");
        }
        if (triple.endOffset() == null) {
            throw invalidItem(index, "endOffset 必须是 32 位整数");
        }
        if (triple.confidence() == null || !Double.isFinite(triple.confidence())) {
            throw invalidItem(index, "confidence 必须是有限数");
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
        validateAttributes(triple.attributes(), index);
    }

    private void validateString(String value, String field, int maxLength, int index) {
        if (value == null) {
            throw invalidItem(index, field + " 必须是字符串");
        }
        if (value.length() > maxLength) {
            throw invalidItem(index, field + " 超过最大长度 " + maxLength);
        }
    }

    private void validateAttributes(Map<String, Object> attributes, int index) {
        if (attributes == null) {
            throw invalidItem(index, "attributes 必须是 JSON 对象或 null");
        }
        if (attributes.size() > EntityExtractionPrompt.MAX_ATTRIBUTE_COUNT) {
            throw invalidItem(index, "attributes 键数量超过上限");
        }
        if (JsonUtils.toJsonString(attributes).getBytes(StandardCharsets.UTF_8).length
                > EntityExtractionPrompt.MAX_ATTRIBUTES_LENGTH) {
            throw invalidItem(index, "attributes 超过最大长度");
        }
        attributes.forEach(
                (key, value) -> {
                    if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) {
                        throw invalidItem(index, "attributes 包含非法键: " + key);
                    }
                    if (EntityExtractionPrompt.RESERVED_ATTRIBUTE_KEYS.contains(key)) {
                        throw invalidItem(index, "attributes 包含保留键: " + key);
                    }
                    validateAttributeValue(value, index, key);
                });
    }

    private void validateAttributeValue(Object value, int index, String key) {
        if (value == null) {
            throw invalidItem(index, "attributes 值不能为 null: " + key);
        }
        if (value instanceof String || value instanceof Boolean) {
            return;
        }
        if (value instanceof Number number) {
            if (!Double.isFinite(number.doubleValue())) {
                throw invalidItem(index, "attributes 数值必须有限: " + key);
            }
            return;
        }
        if (value instanceof List<?> values) {
            for (var item : values) {
                if (item instanceof String || item instanceof Boolean) {
                    continue;
                }
                if (item instanceof Number number && Double.isFinite(number.doubleValue())) {
                    continue;
                }
                throw invalidItem(index, "attributes 数组只能包含非空标量: " + key);
            }
            return;
        }
        throw invalidItem(index, "attributes 不支持嵌套对象或数组: " + key);
    }

    private IllegalArgumentException invalidItem(int index, String message) {
        return new NonRetryableTaskException("事实抽取结果第 " + index + " 项无效: " + message);
    }

    void validateFocusEvidence(String focus, ExtractedTriple triple) {
        if (triple.evidenceQuote() == null || triple.evidenceQuote().isEmpty()) {
            throw new NonRetryableTaskException("事实缺少焦点证据");
        }
        if (triple.startOffset() == null
                || triple.endOffset() == null
                || triple.startOffset() < 0
                || triple.endOffset() <= triple.startOffset()
                || triple.endOffset() > focus.length()) {
            throw new NonRetryableTaskException("事实证据字符区间越界");
        }
        var actual = focus.substring(triple.startOffset(), triple.endOffset());
        if (!actual.equals(triple.evidenceQuote())) {
            throw new NonRetryableTaskException("事实证据必须逐字来自焦点块");
        }
        if (TrustedKnowledgeStore.normalize(triple.subject()).isBlank()
                || TrustedKnowledgeStore.normalize(triple.predicate()).isBlank()
                || TrustedKnowledgeStore.normalize(triple.object()).isBlank()) {
            throw new NonRetryableTaskException("事实规范化后不能为空");
        }
        if (triple.confidence() == null
                || !Double.isFinite(triple.confidence())
                || triple.confidence() < 0
                || triple.confidence() > 1) {
            throw new NonRetryableTaskException("事实置信度必须是 0 到 1 之间的有限数");
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
