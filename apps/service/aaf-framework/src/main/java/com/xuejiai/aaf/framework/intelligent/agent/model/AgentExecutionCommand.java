package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AgentId;

/** 一次可恢复 Agent 回合的纯 AAF 输入。 */
public record AgentExecutionCommand(
        AgentId agentId,
        long definitionVersion,
        long sequenceBase,
        List<AgentMessage> messages,
        InvocationContext context) {

    public AgentExecutionCommand {
        Objects.requireNonNull(agentId, "agentId 不能为空");
        Objects.requireNonNull(context, "context 不能为空");
        if (definitionVersion < 1) {
            throw new IllegalArgumentException("definitionVersion 必须大于 0");
        }
        if (sequenceBase < 0) {
            throw new IllegalArgumentException("sequenceBase 不能小于 0");
        }
        messages = List.copyOf(Objects.requireNonNull(messages, "messages 不能为空"));
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空");
        }
    }
}
