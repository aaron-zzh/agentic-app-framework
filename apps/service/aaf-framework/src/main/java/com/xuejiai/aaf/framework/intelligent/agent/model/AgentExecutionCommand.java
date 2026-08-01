package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;

/** 一次可恢复 Agent 回合的纯 AAF 输入。 */
public record AgentExecutionCommand(
        SubagentSpec subagentSpec,
        Optional<RoleAssignment> roleAssignment,
        ExecutionMode executionMode,
        Optional<ModelSpec> executionModel,
        String skillSystemPromptAppendix,
        Set<String> roleAllowedToolNames,
        long sequenceBase,
        List<AgentMessage> messages,
        InvocationContext context) {

    public AgentExecutionCommand {
        Objects.requireNonNull(subagentSpec, "subagentSpec 不能为空");
        roleAssignment = Objects.requireNonNull(roleAssignment, "roleAssignment Optional 不能为空");
        Objects.requireNonNull(executionMode, "executionMode 不能为空");
        executionModel = Objects.requireNonNull(executionModel, "executionModel Optional 不能为空");
        skillSystemPromptAppendix =
                Objects.requireNonNull(skillSystemPromptAppendix, "skillSystemPromptAppendix 不能为空")
                        .trim();
        roleAllowedToolNames =
                Set.copyOf(
                        Objects.requireNonNull(roleAllowedToolNames, "roleAllowedToolNames 不能为空"));
        roleAllowedToolNames.forEach(
                name -> {
                    if (name.isBlank()) {
                        throw new IllegalArgumentException("roleAllowedToolNames 不能包含空白名称");
                    }
                });
        Objects.requireNonNull(context, "context 不能为空");
        if (sequenceBase < 0) {
            throw new IllegalArgumentException("sequenceBase 不能小于 0");
        }
        messages = List.copyOf(Objects.requireNonNull(messages, "messages 不能为空"));
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空");
        }
    }

    /** 将前注意选出的任务 Role 与命中技能共同编译进子智能体系统提示。 */
    public String effectiveSystemPromptAppendix() {
        var rolePrompt = roleAssignment.map(RoleAssignment::systemPromptAppendix).orElse("");
        if (rolePrompt.isBlank()) {
            return skillSystemPromptAppendix;
        }
        if (skillSystemPromptAppendix.isBlank()) {
            return rolePrompt;
        }
        return rolePrompt + "\n\n" + skillSystemPromptAppendix;
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
