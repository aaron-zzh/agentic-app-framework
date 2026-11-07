package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.FocusExtractionContextAssembler.FocusContext;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.IncrementalEntityResolver;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.IncrementalEntityResolver.EntityMention;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.IncrementalEntityResolver.MentionKey;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeAiMeter;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.FactAssertion;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.RunContext;
import com.xuejiai.aaf.framework.engine.meta.runtime.NonRetryableTaskException;
import com.xuejiai.aaf.framework.intelligent.ai.chat.DashScopeStructuredOutputClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 从焦点块抽取可验证事实并写入 PostgreSQL 真理表。
 *
 * <p>分两步：{@link #extract} 逐块调用大模型抽取并校验，不落库；{@link #persist} 汇总整篇文档的实体提及做一次批量消歧，再写入事实。
 *
 * <p>抽取通过百炼 JSON Schema 结构化输出约束生成，抽取模型须为百炼千问模型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityExtractionService {

    private final DashScopeStructuredOutputClient structuredOutputClient;
    private final KnowledgeAiMeter meter;
    private final IncrementalEntityResolver entityResolver;
    private final TrustedKnowledgeStore store;

    /** 抽取单个焦点块的事实并完成契约与证据校验，返回可持久化的事实。 */
    public ChunkFacts extract(RunContext run, FocusContext context, Runnable executionGuard) {
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
                        () -> {
                            var result =
                                    structuredOutputClient.call(
                                            run.extractionModelId(),
                                            extractionSystemPrompt,
                                            prompt,
                                            EntityExtractionPrompt.OUTPUT_SCHEMA_NAME,
                                            EntityExtractionPrompt.OUTPUT_SCHEMA,
                                            ExtractedFacts.class);
                            var facts = result.entity() == null ? null : result.entity().facts();
                            return new ResponseEntity<>(
                                    result.response(),
                                    facts == null ? List.<ExtractedTriple>of() : facts);
                        });
        if (log.isDebugEnabled()) {
            log.debug(
                    "[KnowledgeExtraction] 结构化返回: runId={}, chunkId={}, count={}, result={}",
                    run.runId(),
                    context.focusChunkId(),
                    extractedTriples == null ? null : extractedTriples.size(),
                    JsonUtils.toJsonString(extractedTriples));
        }
        executionGuard.run();
        return new ChunkFacts(context, validateAndPrepare(run, context, extractedTriples));
    }

    /** 对整篇文档的事实做一次批量实体消歧并写入；消歧失败的实体所涉事实被丢弃。返回写入事实数。 */
    public int persist(RunContext run, List<ChunkFacts> chunks, Runnable executionGuard) {
        var mentions = new ArrayList<EntityMention>();
        for (var chunk : chunks) {
            for (var triple : chunk.triples()) {
                mentions.add(subjectMention(triple));
                if (!"LITERAL".equals(triple.objectKind())) {
                    mentions.add(objectMention(triple));
                }
            }
        }
        if (mentions.isEmpty()) {
            return 0;
        }
        var resolved = entityResolver.resolveAll(run, mentions, executionGuard);

        var persisted = 0;
        for (var chunk : chunks) {
            var context = chunk.context();
            for (var triple : chunk.triples()) {
                executionGuard.run();
                var subject = resolved.get(MentionKey.of(subjectMention(triple)));
                var literal = "LITERAL".equals(triple.objectKind());
                var object = literal ? null : resolved.get(MentionKey.of(objectMention(triple)));
                if (subject == null || (!literal && object == null)) {
                    log.warn(
                            "[KnowledgeExtraction] 丢弃实体未消歧事实: runId={}, chunkId={}, subject={}, predicate={}, object={}",
                            run.runId(),
                            context.focusChunkId(),
                            triple.subject(),
                            triple.predicate(),
                            triple.object());
                    continue;
                }
                var assertion =
                        new FactAssertion(
                                triple.validAt(), triple.invalidAt(), triple.attributes());
                if (literal) {
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
        }
        return persisted;
    }

    private EntityMention subjectMention(ExtractedTriple triple) {
        return new EntityMention(
                triple.subject(), triple.subjectType().name(), triple.subjectDescription());
    }

    private EntityMention objectMention(ExtractedTriple triple) {
        return new EntityMention(triple.object(), triple.objectType(), triple.objectDescription());
    }

    /** 单个焦点块校验后的事实。 */
    public record ChunkFacts(FocusContext context, List<ExtractedTriple> triples) {
        public ChunkFacts {
            triples = List.copyOf(triples);
        }
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
        } else {
            validateLiteralValue(triple.object(), index);
        }
        if (triple.validAt() != null
                && triple.invalidAt() != null
                && !triple.validAt().isBefore(triple.invalidAt())) {
            throw invalidItem(index, "validAt 必须早于 invalidAt");
        }
        validateAttributes(triple.attributes(), index);
    }

    /** LITERAL 只允许短值：去首尾空白后不超过最大长度，且不含句子分隔字符。 */
    private void validateLiteralValue(String object, int index) {
        var value = object.trim();
        if (value.codePointCount(0, value.length()) > EntityExtractionPrompt.MAX_LITERAL_LENGTH) {
            throw invalidItem(
                    index, "LITERAL 的 object 超过最大长度 " + EntityExtractionPrompt.MAX_LITERAL_LENGTH);
        }
        if (value.chars()
                .anyMatch(
                        character ->
                                EntityExtractionPrompt.LITERAL_SENTENCE_DELIMITERS.indexOf(
                                                character)
                                        >= 0)) {
            throw invalidItem(index, "LITERAL 的 object 含句子分隔字符，应拆为实体加关系");
        }
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
