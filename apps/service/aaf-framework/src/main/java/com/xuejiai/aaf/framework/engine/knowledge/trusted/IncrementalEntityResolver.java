package com.xuejiai.aaf.framework.engine.knowledge.trusted;

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
import com.xuejiai.aaf.framework.engine.meta.runtime.NonRetryableTaskException;
import com.xuejiai.aaf.framework.intelligent.ai.chat.DynamicChatClientFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 当前代际新增实体提及的有限候选消歧器。 */
@Slf4j
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
                    "不支持的实体消歧输出契约: " + run.entityResolutionOutputContractVersion());
        }
        var payload = JsonUtils.toJsonString(Map.of("mention", mention, "candidates", candidates));
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
        var decisionOutput =
                meter.invokeEntityResolution(
                        run.billing(),
                        "knowledge-resolution",
                        "mention:"
                                + normalizedAlias
                                + ":"
                                + EntityResolutionPrompt.STRUCTURED_OUTPUT_FORMAT_VERSION,
                        run.entityResolutionModelId(),
                        meteredInput,
                        EntityResolutionPrompt.MAX_RESPONSE_LENGTH,
                        () ->
                                chatClientFactory
                                        .get(run.entityResolutionModelId())
                                        .prompt()
                                        .system(resolutionSystemPrompt)
                                        .user(prompt)
                                        .call()
                                        .responseEntity(
                                                KnowledgeAiMeter.EntityResolutionDecision.class));
        if (log.isDebugEnabled()) {
            log.debug(
                    "[KnowledgeResolution] 结构化返回: runId={}, mention={}, candidates={}, decision={}",
                    run.runId(),
                    JsonUtils.toJsonString(mention),
                    JsonUtils.toJsonString(candidates),
                    JsonUtils.toJsonString(decisionOutput));
        }
        var decision = validateDecision(decisionOutput, candidates);
        return switch (decision.action()) {
            case "LINK" -> link(run, normalizedAlias, decision.entityId());
            case "CREATE" -> create(run, mention, normalizedAlias, false, mentionEmbedding);
            case "REVIEW" -> create(run, mention, normalizedAlias, true, mentionEmbedding);
            default -> throw new IllegalStateException("未处理的实体消歧 action: " + decision.action());
        };
    }

    private ValidatedResolutionDecision validateDecision(
            KnowledgeAiMeter.EntityResolutionDecision decision, List<EntityCandidate> candidates) {
        if (decision == null) {
            throw new InvalidEntityResolutionException("实体消歧结构化结果为空");
        }
        var action = decision.action();
        if (action == null || !EntityResolutionPrompt.ACTIONS.contains(action)) {
            throw new InvalidEntityResolutionException("实体消歧 action 不受支持: " + action);
        }
        if ("LINK".equals(action)) {
            if (decision.entityId() == null || decision.entityId().isBlank()) {
                throw new InvalidEntityResolutionException("LINK 的 entityId 必须是候选 UUID 字符串");
            }
            var entityId = parseUuid(decision.entityId());
            if (candidates.stream().noneMatch(candidate -> candidate.id().equals(entityId))) {
                throw new InvalidEntityResolutionException("LINK 的 entityId 不在当前候选集合中");
            }
            return new ValidatedResolutionDecision(action, entityId);
        }
        if (decision.entityId() != null) {
            throw new InvalidEntityResolutionException(action + " 的 entityId 必须是 null");
        }
        return new ValidatedResolutionDecision(action, null);
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException failure) {
            throw new InvalidEntityResolutionException("LINK 的 entityId 不是有效 UUID", failure);
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

    public static final class InvalidEntityResolutionException extends NonRetryableTaskException {

        public InvalidEntityResolutionException(String message) {
            super(message);
        }

        public InvalidEntityResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record ValidatedResolutionDecision(String action, UUID entityId) {}
}
