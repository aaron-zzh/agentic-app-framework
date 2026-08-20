package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent;
import com.xuejiai.aaf.framework.intelligent.assistant.model.InvocationPolicy;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextRequest.KnowledgeQuery;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextRequest.TaskMaterial;

/** 与命令共同持久化的调用画像，供委派子执行和恢复复用。 */
public record InvocationProfile(
        String requestedSkillKey,
        AssistantInvocation.MemoryMode memoryMode,
        InvocationPolicy invocationPolicy,
        List<AgentMessage.Attachment> userAttachments,
        ExecutionIntent executionIntent,
        ContextPlan contextPlan,
        AgentKind agentKind,
        String agentKey,
        Long expectedAssistantRevision,
        boolean toolScopeRestricted,
        Set<String> allowedToolKeys) {

    public InvocationProfile {
        requestedSkillKey = normalize(requestedSkillKey);
        Objects.requireNonNull(memoryMode, "memoryMode 不能为空");
        Objects.requireNonNull(invocationPolicy, "invocationPolicy 不能为空");
        userAttachments =
                List.copyOf(Objects.requireNonNull(userAttachments, "userAttachments 不能为空"));
        Objects.requireNonNull(executionIntent, "executionIntent 不能为空");
        Objects.requireNonNull(contextPlan, "contextPlan 不能为空");
        Objects.requireNonNull(agentKind, "agentKind 不能为空");
        var expectedPolicy =
                switch (agentKind) {
                    case PRIMARY -> InvocationPolicy.PRIMARY;
                    case COORDINATOR -> InvocationPolicy.COORDINATOR;
                    case EXECUTOR -> InvocationPolicy.EXECUTOR;
                    case AGGREGATOR -> InvocationPolicy.AGGREGATOR;
                };
        if (invocationPolicy != expectedPolicy) {
            throw new IllegalArgumentException("agentKind 与 invocationPolicy 不一致");
        }
        agentKey = requireText(agentKey, "agentKey");
        if (expectedAssistantRevision != null && expectedAssistantRevision < 0) {
            throw new IllegalArgumentException("expectedAssistantRevision 不能小于 0");
        }
        allowedToolKeys =
                Set.copyOf(Objects.requireNonNull(allowedToolKeys, "allowedToolKeys 不能为空"));
        if (allowedToolKeys.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("allowedToolKeys 不能包含空值");
        }
        if (!toolScopeRestricted && !allowedToolKeys.isEmpty()) {
            throw new IllegalArgumentException("未启用工具 scope 时不允许预置 allowlist");
        }
    }

    public static InvocationProfile primary(
            String requestedSkillKey,
            AssistantInvocation.MemoryMode memoryMode,
            List<AgentMessage.Attachment> userAttachments,
            ExecutionIntent executionIntent) {
        return primary(
                requestedSkillKey,
                memoryMode,
                userAttachments,
                executionIntent,
                ContextPlan.empty());
    }

    public static InvocationProfile primary(
            String requestedSkillKey,
            AssistantInvocation.MemoryMode memoryMode,
            List<AgentMessage.Attachment> userAttachments,
            ExecutionIntent executionIntent,
            ContextPlan contextPlan) {
        return new InvocationProfile(
                requestedSkillKey,
                memoryMode,
                InvocationPolicy.PRIMARY,
                userAttachments,
                executionIntent,
                contextPlan,
                AgentKind.PRIMARY,
                "primary",
                null,
                false,
                Set.of());
    }

    public InvocationProfile forCoordinator(String coordinatorKey) {
        return new InvocationProfile(
                requestedSkillKey,
                AssistantInvocation.MemoryMode.DISABLED,
                InvocationPolicy.COORDINATOR,
                List.of(),
                executionIntent,
                contextPlan,
                AgentKind.COORDINATOR,
                coordinatorKey,
                expectedAssistantRevision,
                toolScopeRestricted,
                allowedToolKeys);
    }

    public InvocationProfile forAggregator(String aggregatorKey) {
        return new InvocationProfile(
                requestedSkillKey,
                AssistantInvocation.MemoryMode.DISABLED,
                InvocationPolicy.AGGREGATOR,
                List.of(),
                executionIntent,
                contextPlan,
                AgentKind.AGGREGATOR,
                aggregatorKey,
                expectedAssistantRevision,
                toolScopeRestricted,
                allowedToolKeys);
    }

    public InvocationProfile forExecutor(String executorKey) {
        return new InvocationProfile(
                requestedSkillKey,
                memoryMode,
                InvocationPolicy.EXECUTOR,
                userAttachments,
                executionIntent,
                contextPlan,
                AgentKind.EXECUTOR,
                executorKey,
                expectedAssistantRevision,
                toolScopeRestricted,
                allowedToolKeys);
    }

    public InvocationProfile forAssistantTarget(
            long assistantRevision, String roleKey, String skillKey, Set<String> toolAllowlist) {
        var fixedIntent =
                new ExecutionIntent(
                        executionIntent.interactionMode(),
                        ExecutionIntent.RouteConstraint.FIXED,
                        executionIntent.clarificationPolicy(),
                        new ExecutionIntent.ResolvedRoute(roleKey, skillKey, assistantRevision),
                        executionIntent.artifactPolicy(),
                        executionIntent.actionAuthorizationPolicy(),
                        executionIntent.workspaceId());
        return new InvocationProfile(
                skillKey,
                memoryMode,
                invocationPolicy,
                userAttachments,
                fixedIntent,
                contextPlan,
                agentKind,
                agentKey,
                assistantRevision,
                true,
                toolAllowlist);
    }

    public record ContextPlan(List<TaskMaterial> taskMaterials, KnowledgeQuery knowledgeQuery) {
        public ContextPlan {
            taskMaterials =
                    List.copyOf(Objects.requireNonNull(taskMaterials, "taskMaterials 不能为空"));
            Objects.requireNonNull(knowledgeQuery, "knowledgeQuery 不能为空");
        }

        public static ContextPlan empty() {
            return new ContextPlan(List.of(), KnowledgeQuery.none());
        }
    }

    public enum AgentKind {
        PRIMARY,
        COORDINATOR,
        EXECUTOR,
        AGGREGATOR
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String field) {
        value = normalize(value);
        if (value == null) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value;
    }
}
