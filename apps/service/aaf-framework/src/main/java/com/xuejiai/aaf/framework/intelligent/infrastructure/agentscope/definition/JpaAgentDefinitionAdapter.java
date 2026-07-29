package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.definition;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinitionRepository;
import com.xuejiai.aaf.framework.intelligent.agent.model.AgentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.model.ExecutionPolicy;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AgentId;

/** 将持久化 Agent 定义映射为 P2 运行规格。 */
public final class JpaAgentDefinitionAdapter implements AgentDefinitionPort {

    private static final int DEFAULT_MODEL_RETRIES = 2;

    private final AgentDefinitionRepository repository;

    public JpaAgentDefinitionAdapter(AgentDefinitionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
    }

    @Override
    public Optional<AgentSpec> findByIdAndVersion(AgentId agentId, long version) {
        Objects.requireNonNull(agentId, "agentId 不能为空");
        if (version > Integer.MAX_VALUE) {
            return Optional.empty();
        }
        return repository
                .findByAgentIdAndVersion(agentId.value(), (int) version)
                .filter(entity -> "active".equals(entity.getStatus()))
                .map(this::toSpec);
    }

    private AgentSpec toSpec(AgentDefinition entity) {
        if (entity.getModelId() == null) {
            throw new IllegalStateException("Agent 定义未绑定模型: " + entity.getAgentId());
        }
        var tools =
                JsonUtils.parseArray(entity.getAllowedTools(), String.class).stream()
                        .map(name -> new ToolRef(name, 1, name))
                        .toList();
        return new AgentSpec(
                new AgentId(entity.getAgentId()),
                entity.getVersion(),
                entity.getName(),
                Objects.requireNonNullElse(entity.getDescription(), ""),
                entity.getSystemPrompt(),
                new ModelSpec(entity.getModelId().toString()),
                tools,
                new ExecutionPolicy(
                        entity.getMaxIterations(),
                        DEFAULT_MODEL_RETRIES,
                        Duration.ofSeconds(entity.getTimeoutSeconds())));
    }
}
