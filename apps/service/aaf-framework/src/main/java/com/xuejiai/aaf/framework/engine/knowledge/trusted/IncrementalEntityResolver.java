package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingProperties;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityResolutionPrompt;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.EntityCandidate;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.RunContext;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.ScoredEntityCandidate;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskExecutionInProgressException;
import com.xuejiai.aaf.framework.intelligent.ai.chat.StructuredToolCallClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文档级批量实体消歧器。
 *
 * <p>流程：同一文档内按「规范化类型 + 名称」去重 → 同类型精确别名命中直接复用 → 其余 mention 一次批量向量化 → 按向量距离分档（无候选新建、 高相似同类型自动关联）→
 * 仅中间区间分批交给大模型判定。
 *
 * <p>确定性决策按顺序即时落库，后续 mention 可召回前面新建的实体；同一批大模型判定的 mention 之间互不可见。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(EntityResolutionProperties.class)
public class IncrementalEntityResolver {

    private static final int CANDIDATE_LIMIT = 5;

    private static final String DEFAULT_ENTITY_TYPE = "Concept";

    private final TrustedKnowledgeStore store;
    private final StructuredToolCallClient structuredToolCallClient;
    private final KnowledgeAiMeter meter;
    private final EmbeddingService embeddingService;
    private final EmbeddingProperties embeddingProperties;
    private final EntityResolutionProperties resolutionProperties;

    /**
     * 批量消歧文档内全部实体提及。
     *
     * @return mention 键到实体 ID 的映射；消歧失败或结果无效的 mention 不在结果中，由调用方丢弃相关事实
     */
    public Map<MentionKey, UUID> resolveAll(
            RunContext run, Collection<EntityMention> mentions, Runnable executionGuard) {
        if (!EntityResolutionPrompt.OUTPUT_CONTRACT_VERSION.equals(
                run.entityResolutionOutputContractVersion())) {
            throw new IllegalStateException(
                    "不支持的实体消歧输出契约: " + run.entityResolutionOutputContractVersion());
        }
        var unique = new LinkedHashMap<MentionKey, EntityMention>();
        for (var mention : mentions) {
            var key = MentionKey.of(mention);
            if (key.normalizedAlias().isBlank()) {
                log.warn(
                        "[KnowledgeResolution] 丢弃空实体提及: runId={}, mention={}",
                        run.runId(),
                        mention);
                continue;
            }
            unique.putIfAbsent(key, mention);
        }

        var resolved = new LinkedHashMap<MentionKey, UUID>();
        var ambiguous = new ArrayList<PendingMention>();
        var needEmbedding = new ArrayList<Map.Entry<MentionKey, EntityMention>>();
        for (var entry : unique.entrySet()) {
            executionGuard.run();
            var exact =
                    store.findExactEntities(
                            run.knowledgeBaseId(),
                            entry.getKey().normalizedAlias(),
                            entry.getKey().normalizedType());
            if (exact.size() == 1) {
                resolved.put(entry.getKey(), exact.getFirst().id());
            } else if (exact.size() > 1) {
                ambiguous.add(new PendingMention(entry.getKey(), entry.getValue(), exact, null));
            } else {
                needEmbedding.add(entry);
            }
        }

        var embeddings = embed(run, needEmbedding);
        for (var index = 0; index < needEmbedding.size(); index++) {
            executionGuard.run();
            var key = needEmbedding.get(index).getKey();
            var mention = needEmbedding.get(index).getValue();
            var embedding = embeddings.get(index);
            // 前面的 mention 可能已新建同名同类型实体，先复查精确别名
            var exact =
                    store.findExactEntities(
                            run.knowledgeBaseId(), key.normalizedAlias(), key.normalizedType());
            if (exact.size() == 1) {
                resolved.put(key, exact.getFirst().id());
                continue;
            }
            if (exact.size() > 1) {
                ambiguous.add(new PendingMention(key, mention, exact, embedding));
                continue;
            }
            var candidates =
                    store.findEntityCandidates(
                            run.knowledgeBaseId(),
                            key.normalizedAlias(),
                            key.normalizedType(),
                            embedding,
                            resolutionProperties.candidateMaxDistance(),
                            CANDIDATE_LIMIT);
            var decided = decideDeterministically(run, key, mention, candidates, embedding);
            if (decided != null) {
                resolved.put(key, decided);
            } else {
                ambiguous.add(
                        new PendingMention(
                                key,
                                mention,
                                candidates.stream().map(ScoredEntityCandidate::candidate).toList(),
                                embedding));
            }
        }

        for (var start = 0;
                start < ambiguous.size();
                start += EntityResolutionPrompt.MAX_BATCH_SIZE) {
            executionGuard.run();
            var batch =
                    ambiguous.subList(
                            start,
                            Math.min(
                                    start + EntityResolutionPrompt.MAX_BATCH_SIZE,
                                    ambiguous.size()));
            resolved.putAll(resolveBatch(run, batch));
        }
        return resolved;
    }

    private List<float[]> embed(
            RunContext run, List<Map.Entry<MentionKey, EntityMention>> mentions) {
        if (mentions.isEmpty()) {
            return List.of();
        }
        var texts = mentions.stream().map(entry -> semanticText(entry.getValue())).toList();
        var vectors =
                embeddingService.embedKnowledgeBatch(
                        texts,
                        run.billing(),
                        "entity-candidates:"
                                + TrustedKnowledgeStore.sha256(String.join("\n\n", texts))
                                        .substring(0, 24));
        if (vectors.size() != texts.size()) {
            throw new IllegalStateException("实体候选向量数量与 mention 数量不一致");
        }
        return vectors;
    }

    /** 无候选新建、同名同类型唯一候选或高相似同类型首候选直接关联；需大模型判定时返回 null。 */
    private UUID decideDeterministically(
            RunContext run,
            MentionKey key,
            EntityMention mention,
            List<ScoredEntityCandidate> candidates,
            float[] embedding) {
        if (candidates.isEmpty()) {
            return create(run, mention, key.normalizedAlias(), false, embedding);
        }
        var nearest = candidates.getFirst();
        if (candidates.size() == 1 && isDeterministicMatch(mention, nearest.candidate())) {
            store.addAlias(
                    run.knowledgeBaseId(),
                    nearest.candidate().id(),
                    run.runId(),
                    key.normalizedAlias(),
                    0.95);
            return nearest.candidate().id();
        }
        if (nearest.distance() <= resolutionProperties.autoLinkMaxDistance()
                && sameType(mention, nearest.candidate())) {
            log.debug(
                    "[KnowledgeResolution] 高相似度自动关联: runId={}, mention={}, entityId={}, distance={}",
                    run.runId(),
                    mention.name(),
                    nearest.candidate().id(),
                    nearest.distance());
            store.addAlias(
                    run.knowledgeBaseId(),
                    nearest.candidate().id(),
                    run.runId(),
                    key.normalizedAlias(),
                    0.9);
            return nearest.candidate().id();
        }
        return null;
    }

    /** 一次大模型调用判定一批 mention；整批调用失败或单项结果无效时，对应 mention 不产出结果。 */
    private Map<MentionKey, UUID> resolveBatch(RunContext run, List<PendingMention> batch) {
        var items = new ArrayList<Map<String, Object>>(batch.size());
        var byItemKey = new LinkedHashMap<String, PendingMention>();
        for (var index = 0; index < batch.size(); index++) {
            var itemKey = "m" + index;
            var pending = batch.get(index);
            byItemKey.put(itemKey, pending);
            // 有序 Map 保证序列化稳定，计量输入摘要可幂等复用
            var item = new LinkedHashMap<String, Object>();
            item.put("key", itemKey);
            item.put("mention", pending.mention());
            item.put("candidates", pending.candidates());
            items.add(item);
        }
        KnowledgeAiMeter.EntityResolutionBatchDecision output;
        try {
            output = invokeBatch(run, batch, items);
        } catch (TaskExecutionInProgressException inProgress) {
            throw inProgress;
        } catch (RuntimeException failure) {
            log.warn(
                    "[KnowledgeResolution] 批量消歧调用失败，丢弃本批 mention: runId={}, size={}, reason={}",
                    run.runId(),
                    batch.size(),
                    failure.getMessage());
            return Map.of();
        }
        if (log.isDebugEnabled()) {
            log.debug(
                    "[KnowledgeResolution] 结构化返回: runId={}, items={}, decisions={}",
                    run.runId(),
                    JsonUtils.toJsonString(items),
                    JsonUtils.toJsonString(output));
        }
        var decisions = indexDecisions(run, output);
        var resolved = new HashMap<MentionKey, UUID>();
        byItemKey.forEach(
                (itemKey, pending) -> {
                    var decision = decisions.get(itemKey);
                    var invalidReason = validate(decision, pending.candidates());
                    if (invalidReason != null) {
                        log.warn(
                                "[KnowledgeResolution] 丢弃无效消歧结果: runId={}, mention={}, reason={}",
                                run.runId(),
                                pending.mention().name(),
                                invalidReason);
                        return;
                    }
                    resolved.put(pending.key(), apply(run, pending, decision));
                });
        return resolved;
    }

    private KnowledgeAiMeter.EntityResolutionBatchDecision invokeBatch(
            RunContext run, List<PendingMention> batch, List<Map<String, Object>> items) {
        var payload = JsonUtils.toJsonString(Map.of("items", items));
        var prompt = run.entityResolutionUserPrompt().replace("{payload}", payload);
        var resolutionSystemPrompt =
                run.entityResolutionSystemPrompt()
                        + "\n\n"
                        + EntityResolutionPrompt.OUTPUT_INSTRUCTIONS;
        var meteredInput =
                run.entityResolutionPromptDigest()
                        + "|"
                        + run.entityResolutionOutputContractVersion()
                        + "|"
                        + EntityResolutionPrompt.STRUCTURED_OUTPUT_FORMAT_VERSION
                        + "|"
                        + EntityResolutionPrompt.OUTPUT_INSTRUCTIONS
                        + "|"
                        + prompt;
        var unitKey =
                "mentions:"
                        + TrustedKnowledgeStore.sha256(
                                        batch.stream()
                                                .map(pending -> pending.key().value())
                                                .collect(Collectors.joining("\n")))
                                .substring(0, 24)
                        + ":"
                        + EntityResolutionPrompt.STRUCTURED_OUTPUT_FORMAT_VERSION;
        return meter.invokeEntityResolution(
                run.billing(),
                "knowledge-resolution",
                unitKey,
                run.entityResolutionModelId(),
                meteredInput,
                EntityResolutionPrompt.MAX_RESPONSE_LENGTH,
                () ->
                        structuredToolCallClient.call(
                                run.entityResolutionModelId(),
                                resolutionSystemPrompt,
                                prompt,
                                EntityResolutionPrompt.OUTPUT_TOOL_NAME,
                                EntityResolutionPrompt.OUTPUT_TOOL_DESCRIPTION,
                                KnowledgeAiMeter.EntityResolutionBatchDecision.class));
    }

    /** 按 key 索引决策；重复 key 视为无效，不采纳任何一条。 */
    private Map<String, KnowledgeAiMeter.EntityResolutionDecision> indexDecisions(
            RunContext run, KnowledgeAiMeter.EntityResolutionBatchDecision output) {
        var indexed = new HashMap<String, KnowledgeAiMeter.EntityResolutionDecision>();
        if (output == null || output.decisions() == null) {
            return indexed;
        }
        var duplicated = new java.util.HashSet<String>();
        for (var decision : output.decisions()) {
            if (decision == null || decision.key() == null) {
                continue;
            }
            if (indexed.putIfAbsent(decision.key(), decision) != null) {
                duplicated.add(decision.key());
            }
        }
        duplicated.forEach(
                key -> {
                    log.warn(
                            "[KnowledgeResolution] 消歧结果 key 重复: runId={}, key={}",
                            run.runId(),
                            key);
                    indexed.remove(key);
                });
        return indexed;
    }

    /** 校验单条决策，合法返回 null，否则返回原因。 */
    private String validate(
            KnowledgeAiMeter.EntityResolutionDecision decision, List<EntityCandidate> candidates) {
        if (decision == null) {
            return "缺少该 mention 的决策";
        }
        var action = decision.action();
        if (action == null || !EntityResolutionPrompt.ACTIONS.contains(action)) {
            return "action 不受支持: " + action;
        }
        if ("LINK".equals(action)) {
            if (decision.entityId() == null || decision.entityId().isBlank()) {
                return "LINK 的 entityId 必须是候选 UUID 字符串";
            }
            final UUID entityId;
            try {
                entityId = UUID.fromString(decision.entityId());
            } catch (IllegalArgumentException failure) {
                return "LINK 的 entityId 不是有效 UUID";
            }
            if (candidates.stream().noneMatch(candidate -> candidate.id().equals(entityId))) {
                return "LINK 的 entityId 不在当前候选集合中";
            }
            return null;
        }
        if (decision.entityId() != null) {
            return action + " 的 entityId 必须是 null";
        }
        return null;
    }

    private UUID apply(
            RunContext run,
            PendingMention pending,
            KnowledgeAiMeter.EntityResolutionDecision decision) {
        var alias = pending.key().normalizedAlias();
        return switch (decision.action()) {
            case "LINK" -> {
                var entityId = UUID.fromString(decision.entityId());
                store.addAlias(run.knowledgeBaseId(), entityId, run.runId(), alias, 0.9);
                yield entityId;
            }
            case "CREATE" -> create(run, pending.mention(), alias, false, pending.embedding());
            case "REVIEW" -> create(run, pending.mention(), alias, true, pending.embedding());
            default -> throw new IllegalStateException("未处理的实体消歧 action: " + decision.action());
        };
    }

    private boolean isDeterministicMatch(EntityMention mention, EntityCandidate candidate) {
        return TrustedKnowledgeStore.normalize(candidate.name())
                        .equals(TrustedKnowledgeStore.normalize(mention.name()))
                && sameType(mention, candidate);
    }

    private boolean sameType(EntityMention mention, EntityCandidate candidate) {
        return TrustedKnowledgeStore.normalize(candidate.type())
                .equals(TrustedKnowledgeStore.normalize(entityType(mention)));
    }

    /** 实体实际写入的类型；类型为空时回退为 Concept，匹配键与写入值保持一致。 */
    private static String entityType(EntityMention mention) {
        return mention.type() == null || mention.type().isBlank()
                ? DEFAULT_ENTITY_TYPE
                : mention.type();
    }

    private UUID create(
            RunContext run,
            EntityMention mention,
            String normalizedAlias,
            boolean review,
            float[] mentionEmbedding) {
        var entityId =
                store.createOrAliasEntity(
                        run.knowledgeBaseId(),
                        run.runId(),
                        mention.name().trim(),
                        entityType(mention),
                        mention.description(),
                        normalizedAlias,
                        review);
        if (mentionEmbedding != null) {
            store.upsertEntityEmbedding(
                    entityId,
                    run.knowledgeBaseId(),
                    run.runId(),
                    embeddingProperties.model(),
                    mentionEmbedding);
        }
        return entityId;
    }

    private String semanticText(EntityMention mention) {
        return "%s\n%s\n%s"
                .formatted(
                        mention.name(),
                        mention.type() == null ? "" : mention.type(),
                        mention.description() == null ? "" : mention.description());
    }

    public record EntityMention(String name, String type, String description) {}

    /** 文档内 mention 去重键：规范化类型 + 规范化名称；类型按实际写入值（空类型为 Concept）规范化。 */
    public record MentionKey(String normalizedType, String normalizedAlias) {

        public static MentionKey of(EntityMention mention) {
            return new MentionKey(
                    TrustedKnowledgeStore.normalize(entityType(mention)),
                    TrustedKnowledgeStore.normalize(mention.name() == null ? "" : mention.name()));
        }

        String value() {
            return normalizedType + "|" + normalizedAlias;
        }
    }

    private record PendingMention(
            MentionKey key,
            EntityMention mention,
            List<EntityCandidate> candidates,
            float[] embedding) {}
}
