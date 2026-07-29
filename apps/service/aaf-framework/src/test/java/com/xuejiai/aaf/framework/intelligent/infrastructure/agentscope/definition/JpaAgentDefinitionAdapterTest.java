package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinitionRepository;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AgentId;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class JpaAgentDefinitionAdapterTest extends BaseMockitoUnitTest {

    @Mock private AgentDefinitionRepository repository;

    private JpaAgentDefinitionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JpaAgentDefinitionAdapter(repository);
    }

    @Test
    @DisplayName("Given tools 与 allowed_tools 不同 When 加载定义 Then 技术边界使用 allowed_tools")
    void should_map_allowed_tools_as_agent_technical_boundary() {
        var entity = definition();
        entity.setTools("[\"tool.bound-but-not-allowed\"]");
        entity.setAllowedTools("[\"tool.allowed\"]");
        when(repository.findByAgentIdAndVersion("agent.predefined", 1))
                .thenReturn(Optional.of(entity));

        var result = adapter.findByIdAndVersion(new AgentId("agent.predefined"), 1L).orElseThrow();

        assertThat(result.tools()).extracting(tool -> tool.name()).containsExactly("tool.allowed");
    }

    private static AgentDefinition definition() {
        var entity = new AgentDefinition();
        entity.setAgentId("agent.predefined");
        entity.setVersion(1);
        entity.setName("预定义 Agent");
        entity.setDescription("验证 allowed_tools 映射");
        entity.setSystemPrompt("执行预定义任务");
        entity.setModelId(1L);
        entity.setMaxIterations(3);
        entity.setTimeoutSeconds(30);
        entity.setStatus("active");
        return entity;
    }
}
