package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AgentId;

/** 可编译、可版本化的纯 AAF Agent 定义。 */
public record AgentSpec(
        AgentId agentId,
        long version,
        String name,
        String description,
        String systemPrompt,
        ModelSpec model,
        List<ToolRef> tools,
        ExecutionPolicy executionPolicy) {

    public AgentSpec {
        Objects.requireNonNull(agentId, "agentId 不能为空");
        Objects.requireNonNull(name, "name 不能为空");
        Objects.requireNonNull(description, "description 不能为空");
        Objects.requireNonNull(systemPrompt, "systemPrompt 不能为空");
        Objects.requireNonNull(model, "model 不能为空");
        Objects.requireNonNull(executionPolicy, "executionPolicy 不能为空");
        if (version < 1) {
            throw new IllegalArgumentException("Agent 定义版本必须大于 0");
        }
        if (name.isBlank() || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("Agent 名称和系统提示词不能为空白");
        }
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
