package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.spring;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.xuejiai.aaf.framework.intelligent.agent.port.AgentExecutionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantApplicationService;
import com.xuejiai.aaf.framework.intelligent.assistant.application.CompletionValidator;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DefaultCompletionValidator;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DefaultSkillRouter;
import com.xuejiai.aaf.framework.intelligent.assistant.application.InstallSystemAssistantTemplatesUseCase;
import com.xuejiai.aaf.framework.intelligent.assistant.application.SkillRouter;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.EffectiveContextPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.SystemAssistantTemplateContributor;
import com.xuejiai.aaf.framework.intelligent.assistant.port.SystemAssistantTemplateInstaller;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskControlPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskRecoveryDispatchPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskRecoveryPort;
import com.xuejiai.aaf.framework.intelligent.cognition.application.MemoryGovernanceService;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryContextPort;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.spring.AgentScopeInfrastructureAutoConfiguration;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.BuiltinSystemAssistantTemplates;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.AssistantDefinitionVersionRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.AssistantTaskControlRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.ContextSourcePreferenceRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.EffectiveContextManifestRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaAssistantDefinitionAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaEffectiveContextAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaSystemAssistantTemplateInstaller;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaTaskControlAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.ApprovalRecoveryDispatcher;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;

/** Assistant 应用层装配；只依赖稳定 AAF 端口，不依赖 AgentScope。 */
@AutoConfiguration
@AutoConfigureAfter(AgentScopeInfrastructureAutoConfiguration.class)
public class AssistantInfrastructureAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AssistantDefinitionPort.class)
    AssistantDefinitionPort assistantDefinitionPort(AssistantDefinitionVersionRepository repository) {
        return new JpaAssistantDefinitionAdapter(repository);
    }

    @Bean
    @ConditionalOnMissingBean(TaskControlPort.class)
    TaskControlPort assistantTaskControlPort(AssistantTaskControlRepository repository) {
        return new JpaTaskControlAdapter(repository);
    }

    @Bean
    @ConditionalOnMissingBean(SystemAssistantTemplateInstaller.class)
    SystemAssistantTemplateInstaller systemAssistantTemplateInstaller(
            AssistantDefinitionVersionRepository repository) {
        return new JpaSystemAssistantTemplateInstaller(repository);
    }

    @Bean
    @ConditionalOnMissingBean(SkillRouter.class)
    SkillRouter assistantSkillRouter() {
        return new DefaultSkillRouter();
    }

    @Bean
    @ConditionalOnMissingBean(CompletionValidator.class)
    CompletionValidator assistantCompletionValidator() {
        return new DefaultCompletionValidator();
    }

    @Bean
    @ConditionalOnMissingBean(EffectiveContextPort.class)
    EffectiveContextPort assistantEffectiveContextPort(
            ContextSourcePreferenceRepository preferences,
            EffectiveContextManifestRepository manifests) {
        return new JpaEffectiveContextAdapter(preferences, manifests);
    }

    @Bean
    SystemAssistantTemplateContributor builtinSystemAssistantTemplates() {
        return new BuiltinSystemAssistantTemplates();
    }

    @Bean
    @ConditionalOnBean({
        AssistantDefinitionPort.class,
        TaskControlPort.class,
        AgentExecutionPort.class,
        SkillRouter.class,
        EffectiveContextPort.class,
        MemoryContextPort.class,
        MemoryGovernanceService.class,
        CompletionValidator.class,
        ExecutionEventStorePort.class,
        TaskRecoveryPort.class
    })
    @ConditionalOnMissingBean(AssistantCommandPort.class)
    AssistantCommandPort assistantCommandPort(
            AssistantDefinitionPort definitions,
            TaskControlPort tasks,
            SkillRouter skillRouter,
            EffectiveContextPort effectiveContexts,
            MemoryContextPort memoryContexts,
            MemoryGovernanceService memoryGovernance,
            AgentExecutionPort agentExecution,
            CompletionValidator completionValidator,
            ExecutionEventStorePort eventStore,
            TaskRecoveryPort recoveries) {
        return new AssistantApplicationService(
                definitions,
                tasks,
                skillRouter,
                effectiveContexts,
                memoryContexts,
                memoryGovernance,
                agentExecution,
                completionValidator,
                eventStore,
                recoveries);
    }

    @Bean
    @ConditionalOnMissingBean(TaskRecoveryDispatchPort.class)
    TaskRecoveryDispatchPort taskRecoveryDispatchPort(
            TaskRecoveryPort recoveries, AssistantCommandPort commands) {
        return new ApprovalRecoveryDispatcher(recoveries, commands);
    }

    @Bean
    @ConditionalOnBean(SystemAssistantTemplateInstaller.class)
    InstallSystemAssistantTemplatesUseCase installSystemAssistantTemplatesUseCase(
            List<SystemAssistantTemplateContributor> contributors,
            SystemAssistantTemplateInstaller installer) {
        return new InstallSystemAssistantTemplatesUseCase(contributors, installer);
    }

    @Bean
    @ConditionalOnBean(InstallSystemAssistantTemplatesUseCase.class)
    SystemAssistantTemplateBootstrap systemAssistantTemplateBootstrap(
            InstallSystemAssistantTemplatesUseCase useCase) {
        return new SystemAssistantTemplateBootstrap(useCase);
    }
}
