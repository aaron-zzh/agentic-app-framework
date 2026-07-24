package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.spring;

import com.xuejiai.aaf.framework.intelligent.agent.port.AgentDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentExecutionPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolCatalogPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.compiler.AgentScopeSpecCompiler;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.execution.HarnessAgentExecutionAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeEventMapper;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeMessageMapper;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeRuntimeContextMapper;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.state.SpringRedisClientAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool.AgentScopeToolkitFactory;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/** P1 Core + Agent 唯一 AgentScope 基础设施接线。 */
@AutoConfiguration
@ConditionalOnBean({AgentDefinitionPort.class, ToolCatalogPort.class, ToolInvocationPort.class})
public class AgentScopeInfrastructureAutoConfiguration {

    private static final String STATE_KEY_PREFIX = "aaf:agentscope:state:";

    /**
     * 使用官方 RedisAgentStateStore，并复用 Spring 管理的 Redis 连接。
     * 若不存在 Spring Redis 或调用方提供的 AgentStateStore，后续编译器 Bean 将明确启动失败；
     * 不提供文件或内存状态存储。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(AgentStateStore.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    AgentStateStore agentScopeAgentStateStore(StringRedisTemplate redisTemplate) {
        return RedisAgentStateStore.builder()
                .clientAdapter(new SpringRedisClientAdapter(redisTemplate))
                .keyPrefix(STATE_KEY_PREFIX)
                .build();
    }

    @Bean
    AgentScopeMessageMapper agentScopeMessageMapper() {
        return new AgentScopeMessageMapper();
    }

    @Bean
    AgentScopeRuntimeContextMapper agentScopeRuntimeContextMapper() {
        return new AgentScopeRuntimeContextMapper();
    }

    @Bean
    AgentScopeEventMapper agentScopeEventMapper() {
        return new AgentScopeEventMapper();
    }

    @Bean
    AgentScopeToolkitFactory agentScopeToolkitFactory(
            ToolCatalogPort toolCatalog, ToolInvocationPort toolInvocation) {
        return new AgentScopeToolkitFactory(toolCatalog, toolInvocation);
    }

    @Bean(destroyMethod = "close")
    AgentScopeSpecCompiler agentScopeSpecCompiler(
            AgentStateStore stateStore, AgentScopeToolkitFactory toolkitFactory) {
        return new AgentScopeSpecCompiler(stateStore, toolkitFactory);
    }

    @Bean
    @ConditionalOnMissingBean(AgentExecutionPort.class)
    AgentExecutionPort harnessAgentExecutionPort(
            AgentDefinitionPort definitions,
            AgentScopeSpecCompiler compiler,
            AgentScopeMessageMapper messageMapper,
            AgentScopeRuntimeContextMapper contextMapper,
            AgentScopeEventMapper eventMapper) {
        return new HarnessAgentExecutionAdapter(
                definitions, compiler, messageMapper, contextMapper, eventMapper);
    }
}
