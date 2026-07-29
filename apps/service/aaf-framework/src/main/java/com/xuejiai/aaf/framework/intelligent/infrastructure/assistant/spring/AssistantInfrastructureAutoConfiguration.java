package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.spring;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import com.xuejiai.aaf.framework.engine.skill.SkillStore;
import com.xuejiai.aaf.framework.engine.task.agent.AgentTaskRuntime;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentExecutionPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillCatalogPort;
import com.xuejiai.aaf.framework.intelligent.ai.chat.AiAutoConfiguration;
import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantApplicationService;
import com.xuejiai.aaf.framework.intelligent.assistant.application.CompletionValidator;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DefaultCompletionValidator;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DefaultEffectiveSkillResolver;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DefaultSkillRouter;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DelegatedTaskCoordinator;
import com.xuejiai.aaf.framework.intelligent.assistant.application.EffectiveSkillResolver;
import com.xuejiai.aaf.framework.intelligent.assistant.application.InstallSystemAssistantTemplatesUseCase;
import com.xuejiai.aaf.framework.intelligent.assistant.application.SkillRouter;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskDispatchPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.EffectiveContextPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.NotificationPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.RoleDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.SystemAssistantTemplateContributor;
import com.xuejiai.aaf.framework.intelligent.assistant.port.SystemAssistantTemplateInstaller;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskBoardPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskControlPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskRecoveryDispatchPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskRecoveryPort;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiRoleRepository;
import com.xuejiai.aaf.framework.intelligent.cognition.application.MemoryGovernanceService;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryContextPort;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agent.persistence.JpaSkillCatalogAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.spring.AgentScopeInfrastructureAutoConfiguration;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.BuiltinSystemAssistantTemplates;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.AssistantDefinitionVersionRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.AssistantTaskControlRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.ContextSourcePreferenceRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.EffectiveContextManifestRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaAssistantDefinitionAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaEffectiveContextAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaRoleDefinitionAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaSystemAssistantTemplateInstaller;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaTaskControlAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.ApprovalRecoveryDispatcher;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;

/** Assistant 应用层装配；AgentScope 只通过稳定执行端口进入。 */
@AutoConfiguration
@AutoConfigureAfter({
    AgentScopeInfrastructureAutoConfiguration.class,
    AiAutoConfiguration.class
})
public class AssistantInfrastructureAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AssistantDefinitionPort.class)
    AssistantDefinitionPort assistantDefinitionPort(
            AssistantDefinitionVersionRepository repository) {
        return new JpaAssistantDefinitionAdapter(repository);
    }

    @Bean
    @ConditionalOnMissingBean(RoleDefinitionPort.class)
    RoleDefinitionPort roleDefinitionPort(AiRoleRepository repository) {
        return new JpaRoleDefinitionAdapter(repository);
    }

    @Bean
    @ConditionalOnBean(SkillStore.class)
    @ConditionalOnMissingBean(SkillCatalogPort.class)
    SkillCatalogPort skillCatalogPort(SkillStore skillStore) {
        return new JpaSkillCatalogAdapter(skillStore);
    }

    @Bean
    @ConditionalOnBean(SkillCatalogPort.class)
    @ConditionalOnMissingBean(EffectiveSkillResolver.class)
    EffectiveSkillResolver effectiveSkillResolver(SkillCatalogPort skillCatalog) {
        return new DefaultEffectiveSkillResolver(skillCatalog);
    }

    @Bean
    @ConditionalOnMissingBean(TaskControlPort.class)
    TaskControlPort assistantTaskControlPort(
            AssistantTaskControlRepository repository, ConversationLeasePort leases) {
        return new JpaTaskControlAdapter(repository, leases);
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
        TaskBoardPort.class,
        AgentExecutionPort.class,
        SkillRouter.class,
        EffectiveSkillResolver.class,
        EffectiveContextPort.class,
        MemoryContextPort.class,
        MemoryGovernanceService.class,
        CapabilityRouter.class,
        CompletionValidator.class,
        ExecutionEventStorePort.class,
        TaskRecoveryPort.class
    })
    @ConditionalOnMissingBean(AssistantCommandPort.class)
    AssistantCommandPort assistantCommandPort(
            AssistantDefinitionPort definitions,
            TaskControlPort tasks,
            TaskBoardPort taskBoards,
            SkillRouter skillRouter,
            EffectiveSkillResolver effectiveSkillResolver,
            EffectiveContextPort effectiveContexts,
            MemoryContextPort memoryContexts,
            MemoryGovernanceService memoryGovernance,
            AgentExecutionPort agentExecution,
            CapabilityRouter models,
            CompletionValidator completionValidator,
            ExecutionEventStorePort eventStore,
            TaskRecoveryPort recoveries) {
        return new AssistantApplicationService(
                definitions,
                tasks,
                taskBoards,
                skillRouter,
                effectiveSkillResolver,
                effectiveContexts,
                memoryContexts,
                memoryGovernance,
                agentExecution,
                models,
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
    @ConditionalOnBean({
        DelegatedTaskPort.class,
        TaskBoardPort.class,
        ConversationLeasePort.class,
        AssistantCommandPort.class,
        AgentExecutionPort.class,
        ExecutionEventStorePort.class,
        NotificationPort.class,
        DelegatedTaskDispatchPort.class,
        AgentTaskRuntime.class
    })
    @ConditionalOnMissingBean
    DelegatedTaskCoordinator delegatedTaskCoordinator(
            DelegatedTaskPort tasks,
            TaskBoardPort boards,
            ConversationLeasePort leases,
            AssistantCommandPort assistants,
            AgentExecutionPort agentExecutions,
            ExecutionEventStorePort events,
            NotificationPort notifications,
            DelegatedTaskDispatchPort dispatchSignals,
            AgentTaskRuntime agentTaskRuntime,
            Environment environment) {
        var leaseTtl =
                Duration.ofSeconds(
                        environment.getProperty(
                                "aaf.assistant.delegated.lease-seconds", Long.class, 60L));
        return new DelegatedTaskCoordinator(
                tasks,
                boards,
                leases,
                assistants,
                agentExecutions,
                events,
                notifications,
                dispatchSignals,
                agentTaskRuntime,
                Clock.systemUTC(),
                leaseTtl);
    }

    @Bean
    @ConditionalOnBean(DelegatedTaskCoordinator.class)
    @ConditionalOnMissingBean
    DelegatedTaskScheduler delegatedTaskScheduler(
            DelegatedTaskCoordinator coordinator,
            AgentTaskRuntime agentTaskRuntime,
            Environment environment) {
        var defaultWorker =
                ManagementFactory.getRuntimeMXBean().getName() + "-" + UUID.randomUUID();
        var workerId = environment.getProperty("aaf.assistant.delegated.worker-id", defaultWorker);
        return new DelegatedTaskScheduler(coordinator, agentTaskRuntime, workerId);
    }

    @Bean
    @ConditionalOnBean({
        DelegatedTaskCoordinator.class,
        DelegatedTaskPort.class,
        AgentTaskRuntime.class
    })
    SmartInitializingSingleton delegatedTaskAgentTaskRegistration(
            DelegatedTaskCoordinator coordinator,
            DelegatedTaskPort tasks,
            AgentTaskRuntime agentTaskRuntime) {
        return () ->
                agentTaskRuntime.register(new DelegatedTaskAgentTaskAdapter(coordinator, tasks));
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
