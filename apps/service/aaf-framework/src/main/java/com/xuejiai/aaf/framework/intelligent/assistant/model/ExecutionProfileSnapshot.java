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

/**
 * 单次执行冻结的完整运行画像；恢复任务必须复用该快照，不重新解析当前配置。
 *
 * <p>字段分两类：多数字段是执行不变量（身份、路由、意图、模型选择），在一次执行内固定；{@link #skillExecutionProfile}、 {@link
 * #compiledSystemPrompt}、{@link #toolAuthorizationRules}、{@link #contextCompression} 四个是随物理调用
 * 可增长的部分（对应 {@code core/prompt.md} 定义的 {@code PromptEnvelope}），当前仍随整体记录一次性冻结——这四个字段的 accessor
 * 已单独归组在本类型内，便于后续改造把它们移到逐次调用的 envelope 记录，调用方不必跟着改。
 */
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
        InvocationPolicy invocationPolicy,
        boolean longTermMemoryEnabled,
        List<AgentMessage.Attachment> userAttachments,
        Instant frozenAt,
        PerCallProfile perCallProfile) {

    public ExecutionProfileSnapshot {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
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
        Objects.requireNonNull(invocationPolicy, "invocationPolicy 不能为空");
        userAttachments =
                List.copyOf(Objects.requireNonNull(userAttachments, "userAttachments 不能为空"));
        Objects.requireNonNull(frozenAt, "frozenAt 不能为空");
        Objects.requireNonNull(perCallProfile, "perCallProfile 不能为空");
    }

    /** 兼容既有调用方：委托到 {@link #perCallProfile}，本身不持有该字段。 */
    public SkillExecutionProfile skillExecutionProfile() {
        return perCallProfile.skillExecutionProfile();
    }

    public CompiledSystemPrompt compiledSystemPrompt() {
        return perCallProfile.compiledSystemPrompt();
    }

    public Map<String, ToolAuthorizationRule> toolAuthorizationRules() {
        return perCallProfile.toolAuthorizationRules();
    }

    public Optional<ContextCompressionSnapshot> contextCompression() {
        return perCallProfile.contextCompression();
    }

    public ExecutionProfileSnapshot withContextCompression(ContextCompressionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot 不能为空");
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
                invocationPolicy,
                longTermMemoryEnabled,
                userAttachments,
                frozenAt,
                perCallProfile.withContextCompression(snapshot));
    }

    /**
     * 随物理调用可增长的画像部分。
     *
     * <p>目标态是每次物理调用一份（见 {@code PromptEnvelope}）；当前仍随 {@link ExecutionProfileSnapshot}
     * 整体冻结一次，本类型只做结构隔离，不改变现有冻结时机。
     */
    public record PerCallProfile(
            SkillExecutionProfile skillExecutionProfile,
            CompiledSystemPrompt compiledSystemPrompt,
            Map<String, ToolAuthorizationRule> toolAuthorizationRules,
            Optional<ContextCompressionSnapshot> contextCompression) {

        public PerCallProfile {
            Objects.requireNonNull(skillExecutionProfile, "skillExecutionProfile 不能为空");
            Objects.requireNonNull(compiledSystemPrompt, "compiledSystemPrompt 不能为空");
            toolAuthorizationRules =
                    Map.copyOf(
                            Objects.requireNonNull(
                                    toolAuthorizationRules, "toolAuthorizationRules 不能为空"));
            contextCompression =
                    Objects.requireNonNull(contextCompression, "contextCompression Optional 不能为空");
        }

        public PerCallProfile withContextCompression(ContextCompressionSnapshot snapshot) {
            Objects.requireNonNull(snapshot, "snapshot 不能为空");
            if (contextCompression.isPresent()
                    && !contextCompression.orElseThrow().equals(snapshot)) {
                throw new IllegalStateException("执行画像已冻结不同的上下文压缩结果");
            }
            return new PerCallProfile(
                    skillExecutionProfile,
                    compiledSystemPrompt,
                    toolAuthorizationRules,
                    Optional.of(snapshot));
        }
    }
}
