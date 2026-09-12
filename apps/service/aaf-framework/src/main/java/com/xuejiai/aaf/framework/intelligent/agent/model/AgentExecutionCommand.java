package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;

/** 一次可恢复 Agent 回合的纯 AAF 输入。 */
public record AgentExecutionCommand(
        SubagentSpec subagentSpec,
        Optional<RoleAssignment> roleAssignment,
        ExecutionMode executionMode,
        Optional<ModelSpec> executionModel,
        SkillExecutionProfile skillExecutionProfile,
        CompiledSystemPrompt compiledSystemPrompt,
        long sequenceBase,
        List<AgentMessage> messages,
        InvocationContext context,
        boolean resumeStateRequired) {

    public AgentExecutionCommand(
            SubagentSpec subagentSpec,
            Optional<RoleAssignment> roleAssignment,
            ExecutionMode executionMode,
            Optional<ModelSpec> executionModel,
            SkillExecutionProfile skillExecutionProfile,
            CompiledSystemPrompt compiledSystemPrompt,
            long sequenceBase,
            List<AgentMessage> messages,
            InvocationContext context) {
        this(
                subagentSpec,
                roleAssignment,
                executionMode,
                executionModel,
                skillExecutionProfile,
                compiledSystemPrompt,
                sequenceBase,
                messages,
                context,
                false);
    }

    public AgentExecutionCommand {
        Objects.requireNonNull(subagentSpec, "subagentSpec 不能为空");
        roleAssignment = Objects.requireNonNull(roleAssignment, "roleAssignment Optional 不能为空");
        Objects.requireNonNull(executionMode, "executionMode 不能为空");
        executionModel = Objects.requireNonNull(executionModel, "executionModel Optional 不能为空");
        Objects.requireNonNull(skillExecutionProfile, "skillExecutionProfile 不能为空");
        Objects.requireNonNull(compiledSystemPrompt, "compiledSystemPrompt 不能为空");
        Objects.requireNonNull(context, "context 不能为空");
        if (sequenceBase < 0) {
            throw new IllegalArgumentException("sequenceBase 不能小于 0");
        }
        messages = List.copyOf(Objects.requireNonNull(messages, "messages 不能为空"));
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空");
        }
        if (messages.stream().anyMatch(message -> message.role() == AgentMessage.Role.SYSTEM)) {
            throw new IllegalArgumentException(
                    "Agent messages 禁止追加 SYSTEM；SYSTEM 只能来自 CompiledSystemPrompt");
        }
    }

    public enum ExecutionMode {
        DIRECT,
        DELEGATE
    }

    /** Assistant 前注意阶段为当前任务选出、并显式委托给 Agent 的 Role 快照。 */
    public record RoleAssignment(
            String roleKey,
            String roleName,
            List<String> responsibilities,
            List<String> nonResponsibilities) {

        public RoleAssignment {
            roleKey = requireText(roleKey, "roleKey");
            roleName = requireText(roleName, "roleName");
            responsibilities =
                    List.copyOf(Objects.requireNonNull(responsibilities, "responsibilities 不能为空"));
            nonResponsibilities =
                    List.copyOf(
                            Objects.requireNonNull(
                                    nonResponsibilities, "nonResponsibilities 不能为空"));
        }

        public String systemPromptAppendix() {
            return """
                    ## 当前任务角色
                    角色：%s（%s）

                    职责：
                    %s

                    非职责：
                    %s

                    只在上述职责范围内执行，不得越过非职责边界。
                    """
                    .formatted(
                            roleName,
                            roleKey,
                            bulletList(responsibilities),
                            bulletList(nonResponsibilities))
                    .trim();
        }

        private static String bulletList(List<String> items) {
            return items.isEmpty()
                    ? "- 无"
                    : items.stream()
                            .map(item -> "- " + item)
                            .collect(java.util.stream.Collectors.joining("\n"));
        }

        private static String requireText(String value, String field) {
            Objects.requireNonNull(value, field + " 不能为空");
            if (value.isBlank()) {
                throw new IllegalArgumentException(field + " 不能为空白");
            }
            return value;
        }
    }
}
