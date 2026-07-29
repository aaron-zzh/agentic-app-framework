package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AgentId;

/** 技能命中后所委派子智能体的完整领域规格。 */
public sealed interface SubagentSpec permits SubagentSpec.Predefined, SubagentSpec.Dynamic {

    /** 用于任务归属、事件溯源和上下文来源的稳定标识。 */
    String identifier();

    /** 从持久化 Agent 定义加载的治理型子智能体。 */
    record Predefined(AgentId agentId, long version) implements SubagentSpec {
        public Predefined {
            Objects.requireNonNull(agentId, "agentId 不能为空");
            if (version < 1) {
                throw new IllegalArgumentException("version 必须大于 0");
            }
        }

        @Override
        public String identifier() {
            return agentId.value();
        }
    }

    /** 根据当前 Assistant 上下文现场构造、不落库的动态子智能体。 */
    record Dynamic(
            String name,
            String description,
            String systemPromptFragment,
            List<ToolRef> tools,
            ExecutionPolicy executionPolicy,
            boolean inheritParentTools)
            implements SubagentSpec {

        public Dynamic {
            name = requireIdentifier(name, "name");
            description = requireText(description, "description");
            systemPromptFragment = requireText(systemPromptFragment, "systemPromptFragment");
            tools = List.copyOf(Objects.requireNonNull(tools, "tools 不能为空"));
            Objects.requireNonNull(executionPolicy, "executionPolicy 不能为空");
        }

        @Override
        public String identifier() {
            return name;
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value;
    }

    private static String requireIdentifier(String value, String field) {
        value = requireText(value, field);
        if (value.length() > 128 || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException(field + " 必须是稳定机器标识");
        }
        return value;
    }
}
