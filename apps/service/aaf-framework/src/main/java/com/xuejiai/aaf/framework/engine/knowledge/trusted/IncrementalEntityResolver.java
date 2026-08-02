package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingProperties;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityResolutionPrompt;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.EntityCandidate;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.RunContext;
import com.xuejiai.aaf.framework.intelligent.ai.chat.DynamicChatClientFactory;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/** 当前代际新增实体提及的有限候选消歧器。 */
@Service
@RequiredArgsConstructor
public class IncrementalEntityResolver {

    private static final int CANDIDATE_LIMIT = 5;

    private final TrustedKnowledgeStore store;
    private final DynamicChatClientFactory chatClientFactory;
    private final KnowledgeAiMeter meter;
    private final EmbeddingService embeddingService;
    private final EmbeddingProperties embeddingProperties;

    public UUID resolve(RunContext run, EntityMention mention) {
        var normalizedAlias = TrustedKnowledgeStore.normalize(mention.name());
        if (normalizedAlias.isBlank()) {
            throw new IllegalArgumentException("实体提及不能为空");
        }
        var exact = store.findExactEntities(run.knowledgeBaseId(), normalizedAlias);
        if (exact.size() == 1) {
            return exact.getFirst().id();
        }
        if (exact.size() > 1) {
            return resolveAmbiguous(run, mention, normalizedAlias, exact, null);
        }
        var semanticText = semanticText(mention);
        var mentionEmbedding =
                embeddingService
                        .embedKnowledgeBatch(
                                List.of(semanticText),
                                run.billing(),
                                "entity-candidate:"
                                        + TrustedKnowledgeStore.sha256(semanticText)
                                                .substring(0, 24))
                        .getFirst();
        var candidates =
                store.findEntityCandidates(
                        run.knowledgeBaseId(), normalizedAlias, mentionEmbedding, CANDIDATE_LIMIT);
        if (candidates.isEmpty()) {
            return create(run, mention, normalizedAlias, false, mentionEmbedding);
        }
        if (candidates.size() == 1 && isDeterministicMatch(mention, candidates.getFirst())) {
            var matched = candidates.getFirst();
            store.addAlias(run.knowledgeBaseId(), matched.id(), run.runId(), normalizedAlias, 0.95);
            return matched.id();
        }
        return resolveAmbiguous(run, mention, normalizedAlias, candidates, mentionEmbedding);
    }

    private UUID resolveAmbiguous(
            RunContext run,
            EntityMention mention,
            String normalizedAlias,
            List<EntityCandidate> candidates,
            float[] mentionEmbedding) {
        if (!EntityResolutionPrompt.OUTPUT_CONTRACT_VERSION.equals(
                run.entityResolutionOutputContractVersion())) {
            throw new IllegalStateException(
                    "不支持的实体消歧输出契约: "
                            + run.entityResolutionOutputContractVersion());
        }
        var payload = JsonUtils.toJsonString(Map.of("mention", mention, "candidates", candidates));
        var prompt = EntityResolutionPrompt.USER_PROMPT_TEMPLATE.replace("{payload}", payload);
        var meteredInput =
                run.entityResolutionPromptDigest()
                        + "|"
                        + run.entityResolutionOutputContractVersion()
                        + "|"
                        + prompt;
        var decisionText =
                meter.invokeText(
                        run.billing(),
                        "knowledge-resolution",
                        "mention:" + normalizedAlias,
                        run.entityResolutionModelId(),
                        meteredInput,
                        () ->
                                chatClientFactory
                                        .get(run.entityResolutionModelId())
                                        .prompt()
                                        .system(run.entityResolutionSystemPrompt())
                                        .user(prompt)
                                        .call()
                                        .chatResponse());
        var decision = parseDecision(decisionText, candidates);
        return switch (decision.action()) {
            case "LINK" -> link(run, normalizedAlias, decision.entityId());
            case "CREATE" -> create(run, mention, normalizedAlias, false, mentionEmbedding);
            case "REVIEW" -> create(run, mention, normalizedAlias, true, mentionEmbedding);
            default -> throw new IllegalStateException("未处理的实体消歧 action: " + decision.action());
        };
    }

    private ResolutionDecision parseDecision(
            String content, List<EntityCandidate> candidates) {
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("实体消歧结果为空");
        }
        if (content.length() > EntityResolutionPrompt.MAX_RESPONSE_LENGTH) {
            throw new IllegalStateException("实体消歧结果超过最大长度");
        }
        final JsonNode root;
        try {
            root = JsonUtils.readTree(content);
        } catch (Exception failure) {
            throw new IllegalStateException("实体消歧结果不是有效 JSON", failure);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("实体消歧结果顶层必须是 JSON 对象");
        }
        var fields = new LinkedHashSet<String>();
        root.properties().forEach(property -> fields.add(property.getKey()));
        if (!fields.equals(EntityResolutionPrompt.REQUIRED_FIELDS)) {
            var missing = new LinkedHashSet<>(EntityResolutionPrompt.REQUIRED_FIELDS);
            missing.removeAll(fields);
            var unknown = new LinkedHashSet<>(fields);
            unknown.removeAll(EntityResolutionPrompt.REQUIRED_FIELDS);
            throw new IllegalArgumentException(
                    "实体消歧结果字段集合不匹配，缺失=" + missing + "，未知=" + unknown);
        }
        var actionNode = root.get("action");
        if (actionNode == null || !actionNode.isTextual()) {
            throw new IllegalArgumentException("实体消歧 action 必须是字符串");
        }
        var action = actionNode.asString();
        if (!EntityResolutionPrompt.ACTIONS.contains(action)) {
            throw new IllegalArgumentException("实体消歧 action 不受支持: " + action);
        }
        var entityIdNode = root.get("entityId");
        if ("LINK".equals(action)) {
            if (entityIdNode == null || !entityIdNode.isTextual()) {
                throw new IllegalArgumentException("LINK 的 entityId 必须是候选 UUID 字符串");
            }
            var entityId = parseUuid(entityIdNode.asString());
            if (candidates.stream().noneMatch(candidate -> candidate.id().equals(entityId))) {
                throw new IllegalArgumentException("LINK 的 entityId 不在当前候选集合中");
            }
            return new ResolutionDecision(action, entityId);
        }
        if (entityIdNode == null || !entityIdNode.isNull()) {
            throw new IllegalArgumentException(action + " 的 entityId 必须是 null");
        }
        return new ResolutionDecision(action, null);
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("LINK 的 entityId 不是有效 UUID", failure);
        }
    }

    private UUID link(RunContext run, String normalizedAlias, UUID entityId) {
        store.addAlias(run.knowledgeBaseId(), entityId, run.runId(), normalizedAlias, 0.9);
        return entityId;
    }

    private boolean isDeterministicMatch(EntityMention mention, EntityCandidate candidate) {
        return TrustedKnowledgeStore.normalize(candidate.name())
                        .equals(TrustedKnowledgeStore.normalize(mention.name()))
                && TrustedKnowledgeStore.normalize(candidate.type())
                        .equals(TrustedKnowledgeStore.normalize(mention.type()));
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
                        mention.type() == null || mention.type().isBlank()
                                ? "Concept"
                                : mention.type(),
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

    private record ResolutionDecision(String action, UUID entityId) {}
}
