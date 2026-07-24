package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.xuejiai.aaf.framework.engine.tool.ToolCatalogProvider;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinitionRepository;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolCatalogPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelManagementService;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.definition.JpaAgentDefinitionAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.model.AgentScopeModelResolver;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool.RegistryToolPortAdapter;

/** 将现有生产仓储和工具注册中心接入 P2 稳定端口。 */
@AutoConfiguration
@AutoConfigureBefore(AgentScopeInfrastructureAutoConfiguration.class)
public class AgentRuntimePortAutoConfiguration {

    @Bean
    @ConditionalOnBean(AgentDefinitionRepository.class)
    @ConditionalOnMissingBean(AgentDefinitionPort.class)
    AgentDefinitionPort agentDefinitionPort(AgentDefinitionRepository repository) {
        return new JpaAgentDefinitionAdapter(repository);
    }

    @Bean
    @ConditionalOnBean(ModelManagementService.class)
    @ConditionalOnMissingBean(AgentScopeModelResolver.class)
    AgentScopeModelResolver agentScopeModelResolver(ModelManagementService models) {
        return new AgentScopeModelResolver(models);
    }

    @Bean
    @ConditionalOnBean({ToolRegistry.class, ToolCatalogProvider.class})
    @ConditionalOnMissingBean({ToolCatalogPort.class, ToolInvocationPort.class})
    RegistryToolPortAdapter registryToolPortAdapter(
            ToolRegistry registry, ToolCatalogProvider catalog) {
        return new RegistryToolPortAdapter(registry, catalog);
    }
}
