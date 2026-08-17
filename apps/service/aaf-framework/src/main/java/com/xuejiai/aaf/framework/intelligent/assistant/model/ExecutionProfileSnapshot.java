package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand.ExecutionMode;
import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand.RoleAssignment;
import com.xuejiai.aaf.framework.intelligent.agent.model.SkillExecutionProfile;
import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext.ToolAuthorizationRule;
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
        SubagentSpec.Dynamic executionSpec,
        RoleAssignment roleAssignment,
        ExecutionMode executionMode,
        Optional<ModelSpec> executionModel,
        SkillExecutionProfile skillExecutionProfile,
        Map<String, ToolAuthorizationRule> toolAuthorizationRules,
        Instant frozenAt) {

    public ExecutionProfileSnapshot {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Objects.requireNonNull(assistantId, "assistantId 不能为空");
        if (assistantRevision < 0) {
            throw new IllegalArgumentException("assistantRevision 不能小于 0");
        }
        Objects.requireNonNull(executionSpec, "executionSpec 不能为空");
        Objects.requireNonNull(roleAssignment, "roleAssignment 不能为空");
        Objects.requireNonNull(executionMode, "executionMode 不能为空");
        executionModel = Objects.requireNonNull(executionModel, "executionModel Optional 不能为空");
        Objects.requireNonNull(skillExecutionProfile, "skillExecutionProfile 不能为空");
        toolAuthorizationRules =
                Map.copyOf(
                        Objects.requireNonNull(
                                toolAuthorizationRules, "toolAuthorizationRules 不能为空"));
        Objects.requireNonNull(frozenAt, "frozenAt 不能为空");
    }
}
