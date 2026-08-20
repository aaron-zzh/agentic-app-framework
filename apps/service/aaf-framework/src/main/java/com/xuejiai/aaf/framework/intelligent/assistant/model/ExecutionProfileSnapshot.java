package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand.ExecutionMode;
import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand.RoleAssignment;
import com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt;
import com.xuejiai.aaf.framework.intelligent.agent.model.SkillExecutionProfile;
import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext.ToolAuthorizationRule;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextCompressionSnapshot;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** 单次执行冻结的完整运行画像；恢复任务必须复用该快照，不重新解析当前配置。 */
public record ExecutionProfileSnapshot(
        TenantId tenantId,
        TaskId taskId,
        ExecutionId executionId,
        AssistantId assistantId,
        long assistantRevision,
        ExecutionIntent executionIntent,
        ContextDisclosurePolicy contextDisclosurePolicy,
        SubagentSpec.Dynamic executionSpec,
        RoleAssignment roleAssignment,
        ExecutionMode executionMode,
        Optional<ModelSpec> executionModel,
        SkillExecutionProfile skillExecutionProfile,
        InvocationPolicy invocationPolicy,
        CompiledSystemPrompt compiledSystemPrompt,
        boolean longTermMemoryEnabled,
        List<AgentMessage.Attachment> userAttachments,
        Map<String, ToolAuthorizationRule> toolAuthorizationRules,
        Optional<ContextCompressionSnapshot> contextCompression,
        Instant frozenAt) {

    public ExecutionProfileSnapshot {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Objects.requireNonNull(assistantId, "assistantId 不能为空");
        if (assistantRevision < 0) {
            throw new IllegalArgumentException("assistantRevision 不能小于 0");
        }
        Objects.requireNonNull(executionIntent, "executionIntent 不能为空");
        Objects.requireNonNull(contextDisclosurePolicy, "contextDisclosurePolicy 不能为空");
        Objects.requireNonNull(executionSpec, "executionSpec 不能为空");
        Objects.requireNonNull(roleAssignment, "roleAssignment 不能为空");
        Objects.requireNonNull(executionMode, "executionMode 不能为空");
        executionModel = Objects.requireNonNull(executionModel, "executionModel Optional 不能为空");
        Objects.requireNonNull(skillExecutionProfile, "skillExecutionProfile 不能为空");
        Objects.requireNonNull(invocationPolicy, "invocationPolicy 不能为空");
        Objects.requireNonNull(compiledSystemPrompt, "compiledSystemPrompt 不能为空");
        userAttachments =
                List.copyOf(Objects.requireNonNull(userAttachments, "userAttachments 不能为空"));
        toolAuthorizationRules =
                Map.copyOf(
                        Objects.requireNonNull(
                                toolAuthorizationRules, "toolAuthorizationRules 不能为空"));
        contextCompression =
                Objects.requireNonNull(contextCompression, "contextCompression Optional 不能为空");
        Objects.requireNonNull(frozenAt, "frozenAt 不能为空");
    }

    public ExecutionProfileSnapshot withContextCompression(ContextCompressionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot 不能为空");
        if (contextCompression.isPresent() && !contextCompression.orElseThrow().equals(snapshot)) {
            throw new IllegalStateException("执行画像已冻结不同的上下文压缩结果");
        }
        return new ExecutionProfileSnapshot(
                tenantId,
                taskId,
                executionId,
                assistantId,
                assistantRevision,
                executionIntent,
                contextDisclosurePolicy,
                executionSpec,
                roleAssignment,
                executionMode,
                executionModel,
                skillExecutionProfile,
                invocationPolicy,
                compiledSystemPrompt,
                longTermMemoryEnabled,
                userAttachments,
                toolAuthorizationRules,
                Optional.of(snapshot),
                frozenAt);
    }
}
