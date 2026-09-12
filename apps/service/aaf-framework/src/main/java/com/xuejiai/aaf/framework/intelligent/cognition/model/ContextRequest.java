package com.xuejiai.aaf.framework.intelligent.cognition.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage;
import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest.SourceReference;
import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest.SourceType;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationSubject;

/** L1 受控混合上下文请求。 */
public record ContextRequest(
        TenantId tenantId,
        UserId userId,
        TaskId taskId,
        ExecutionId executionId,
        AssistantId assistantId,
        MemorySubject memorySubject,
        AgentPurpose purpose,
        String query,
        Set<ContextScope> scopes,
        ContextBudget budget,
        Disclosure disclosure,
        List<SourceReference> authorizedCandidates,
        List<TaskMaterial> taskMaterials,
        KnowledgeQuery knowledgeQuery,
        String sessionId,
        Instant requestedAt) {

    public ContextRequest {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(userId, "userId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Objects.requireNonNull(assistantId, "assistantId 不能为空");
        Objects.requireNonNull(memorySubject, "memorySubject 不能为空");
        if (!tenantId.equals(memorySubject.tenantId())) {
            throw new IllegalArgumentException("memorySubject tenant 与上下文请求 tenant 不一致");
        }
        Objects.requireNonNull(purpose, "purpose 不能为空");
        query = Objects.requireNonNullElse(query, "").trim();
        scopes = Set.copyOf(Objects.requireNonNull(scopes, "scopes 不能为空"));
        Objects.requireNonNull(budget, "budget 不能为空");
        Objects.requireNonNull(disclosure, "disclosure 不能为空");
        authorizedCandidates =
                List.copyOf(
                        Objects.requireNonNull(authorizedCandidates, "authorizedCandidates 不能为空"));
        taskMaterials = List.copyOf(Objects.requireNonNull(taskMaterials, "taskMaterials 不能为空"));
        knowledgeQuery = Objects.requireNonNull(knowledgeQuery, "knowledgeQuery 不能为空");
        sessionId = sessionId == null || sessionId.isBlank() ? null : sessionId.trim();
        Objects.requireNonNull(requestedAt, "requestedAt 不能为空");
        if ((purpose == AgentPurpose.COORDINATION || purpose == AgentPurpose.AGGREGATION)
                && disclosure != Disclosure.SUMMARY_ONLY) {
            throw new IllegalArgumentException("协调与聚合只能请求摘要上下文");
        }
        // 协调与聚合只消费冻结目标与结果，不得回看用户会话历史
        if (purpose == AgentPurpose.COORDINATION || purpose == AgentPurpose.AGGREGATION) {
            sessionId = null;
        }
        validateScopes(scopes, taskMaterials, knowledgeQuery);
        validateTaskMaterials(authorizedCandidates, taskMaterials);
        validateKnowledgeCandidates(authorizedCandidates, knowledgeQuery);
    }

    private static void validateScopes(
            Set<ContextScope> scopes,
            List<TaskMaterial> taskMaterials,
            KnowledgeQuery knowledgeQuery) {
        if (!taskMaterials.isEmpty() && !scopes.contains(ContextScope.TASK_MATERIAL)) {
            throw new IllegalArgumentException("存在任务材料时必须声明 TASK_MATERIAL scope");
        }
        if (knowledgeQuery.enabled() && !scopes.contains(ContextScope.KNOWLEDGE)) {
            throw new IllegalArgumentException("存在知识查询时必须声明 KNOWLEDGE scope");
        }
    }

    private static void validateTaskMaterials(
            List<SourceReference> authorizedCandidates, List<TaskMaterial> taskMaterials) {
        for (var material : taskMaterials) {
            if (!authorizedCandidates.contains(material.reference())) {
                throw new IllegalArgumentException("任务材料不在 command/contextCandidates 授权集合内");
            }
        }
    }

    private static void validateKnowledgeCandidates(
            List<SourceReference> authorizedCandidates, KnowledgeQuery knowledgeQuery) {
        if (!knowledgeQuery.enabled()) {
            return;
        }
        var candidateIds =
                authorizedCandidates.stream()
                        .filter(reference -> reference.type() == SourceType.KNOWLEDGE)
                        .map(SourceReference::sourceKey)
                        .map(ContextRequest::parseUuid)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!candidateIds.containsAll(knowledgeQuery.knowledgeBaseIds())) {
            throw new IllegalArgumentException("知识查询包含 command/contextCandidates 外知识库");
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(
                    "KNOWLEDGE candidate sourceKey 必须是知识库 UUID", failure);
        }
    }

    public enum AgentPurpose {
        COORDINATION,
        EXECUTION,
        AGGREGATION
    }

    public enum ContextScope {
        MEMORY,
        KNOWLEDGE,
        TASK_MATERIAL
    }

    public enum Disclosure {
        SUMMARY_ONLY,
        CONTENT_ALLOWED
    }

    public record ContextBudget(int maxItems, int characterBudget) {
        public ContextBudget {
            if (maxItems < 1 || maxItems > 32) {
                throw new IllegalArgumentException("上下文 maxItems 必须在 1..32");
            }
            if (characterBudget < 128 || characterBudget > 8_192) {
                throw new IllegalArgumentException("上下文 characterBudget 必须在 128..8192");
            }
        }
    }

    /** 只允许携带已与 command/contextCandidates 绑定的受控请求上下文。 */
    public record TaskMaterial(SourceReference reference, AgentMessage message) {
        private static final Set<SourceType> ALLOWED_SOURCE_TYPES =
                Set.of(SourceType.TASK_MATERIAL, SourceType.RULE, SourceType.SKILL);

        public TaskMaterial {
            Objects.requireNonNull(reference, "reference 不能为空");
            Objects.requireNonNull(message, "message 不能为空");
            if (!ALLOWED_SOURCE_TYPES.contains(reference.type())) {
                throw new IllegalArgumentException(
                        "TaskMaterial reference 仅允许 TASK_MATERIAL、RULE 或 SKILL");
            }
            if (message.role() != AgentMessage.Role.USER || !message.attachments().isEmpty()) {
                throw new IllegalArgumentException("TaskMaterial 只能携带无附件 USER 消息");
            }
        }
    }

    /** 已解析主体与公开 API 查询参数形成的授权知识查询。 */
    public record KnowledgeQuery(
            AuthorizationSubject subject, Set<UUID> knowledgeBaseIds, int topK, double threshold) {

        public KnowledgeQuery {
            knowledgeBaseIds = knowledgeBaseIds == null ? Set.of() : Set.copyOf(knowledgeBaseIds);
            if (!knowledgeBaseIds.isEmpty()) {
                Objects.requireNonNull(subject, "知识查询授权主体不能为空");
                if (subject.operatorId() == null
                        || subject.subjectId() == null
                        || subject.tenantId() == null) {
                    throw new IllegalArgumentException("知识查询禁止使用 unresolved subject");
                }
                if (topK < 1 || topK > 32) {
                    throw new IllegalArgumentException("知识查询 topK 必须在 1..32");
                }
                if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1) {
                    throw new IllegalArgumentException("知识查询 threshold 必须在 0..1");
                }
            }
        }

        public static KnowledgeQuery none() {
            return new KnowledgeQuery(null, Set.of(), 1, 0);
        }

        public boolean enabled() {
            return !knowledgeBaseIds.isEmpty();
        }
    }
}
