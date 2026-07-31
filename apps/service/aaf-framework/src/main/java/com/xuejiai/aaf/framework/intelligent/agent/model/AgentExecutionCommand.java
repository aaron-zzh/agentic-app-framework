package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;

/** 一次可恢复 Agent 回合的纯 AAF 输入。 */
public record AgentExecutionCommand(
        SubagentSpec subagentSpec,
        Optional<ModelSpec> executionModel,
        String skillSystemPromptAppendix,
        Set<String> roleAllowedToolNames,
        long sequenceBase,
        List<AgentMessage> messages,
        InvocationContext context) {

    public AgentExecutionCommand {
        Objects.requireNonNull(subagentSpec, "subagentSpec 不能为空");
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
}
