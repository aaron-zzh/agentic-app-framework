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

/**
 * 将持久化 Agent 定义映射为 P2 运行规格。
 *
 * <p>{@link AgentDefinitionPort} 的 JPA 实现：读 agent_definition 表，把行记录翻译成 {@link AgentSpec}（提示词 + 模型 +
 * 工具引用 + 执行策略），是预定义 Agent 的唯一定义来源。
 */
public final class JpaAgentDefinitionAdapter implements AgentDefinitionPort {

    /** 定义表未存该字段，模型重试次数统一取默认值。 */
    private static final int DEFAULT_MODEL_RETRIES = 2;

    private final AgentDefinitionRepository repository;
    private final int contextWindow;

    public JpaAgentDefinitionAdapter(AgentDefinitionRepository repository, int contextWindow) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        if (contextWindow < 1) {
            throw new IllegalArgumentException("contextWindow 必须大于 0");
        }
        this.contextWindow = contextWindow;
    }

    /** 仅返回 active 版本；非法或越界版本号按"定义不存在"处理。 */
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
        // P2 阶段工具稳定标识 = callback 名称，版本恒为 1
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
                ExecutionPolicy.withDefaultTimeouts(
                        entity.getMaxIterations(),
                        DEFAULT_MODEL_RETRIES,
                        Duration.ofSeconds(entity.getTimeoutSeconds()),
                        contextWindow));
    }
}
