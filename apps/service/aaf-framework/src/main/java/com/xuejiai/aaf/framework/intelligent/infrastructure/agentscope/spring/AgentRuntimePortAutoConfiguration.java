package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.spring;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Bean;

import com.xuejiai.aaf.framework.engine.tool.ScriptExecutor;
import com.xuejiai.aaf.framework.engine.tool.ToolCatalogProvider;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinitionRepository;
import com.xuejiai.aaf.framework.intelligent.agent.application.JavaScriptExecutionTool;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolCatalogPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelManagementService;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.definition.JpaAgentDefinitionAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.model.AgentScopeModelResolver;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool.RegistryToolPortAdapter;

/**
 * 将现有生产仓储和工具注册中心接入 P2 稳定端口。
 *
 * <p>必须晚于 {@link DataJpaRepositoriesAutoConfiguration}，确保 {@link AgentDefinitionRepository}
 * 已注册后再评估端口创建条件；否则 {@link AgentDefinitionPort} 会缺失，后续 AgentScope 与 Assistant 条件装配链将被整体跳过。
 *
 * <p>同时必须早于 {@link AgentScopeInfrastructureAutoConfiguration}——后者以这些端口存在为生效条件。全部 Bean 都带
 * ConditionalOnMissingBean，业务方可自行替换实现。
 */
@AutoConfiguration
@AutoConfigureAfter(DataJpaRepositoriesAutoConfiguration.class)
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
    @ConditionalOnBean(ScriptExecutor.class)
    @ConditionalOnMissingBean(JavaScriptExecutionTool.class)
    JavaScriptExecutionTool javaScriptExecutionTool(ScriptExecutor scriptExecutor) {
        return new JavaScriptExecutionTool(scriptExecutor);
    }

    @Bean
    @ConditionalOnBean({ToolRegistry.class, ToolCatalogProvider.class})
    @ConditionalOnMissingBean({ToolCatalogPort.class, ToolInvocationPort.class})
    RegistryToolPortAdapter registryToolPortAdapter(
            ToolRegistry registry,
            ToolCatalogProvider catalog,
            ObjectProvider<ContextAwareToolHandler> handlers) {
        return new RegistryToolPortAdapter(registry, catalog, handlers);
    }
}
