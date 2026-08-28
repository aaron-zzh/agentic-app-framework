package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.spring;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
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
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillReferenceCatalogPort;
import com.xuejiai.aaf.framework.intelligent.ai.chat.AiAutoConfiguration;
import com.xuejiai.aaf.framework.intelligent.ai.chat.AiProperties;
import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantApplicationService;
import com.xuejiai.aaf.framework.intelligent.assistant.application.CompletionValidator;
import com.xuejiai.aaf.framework.intelligent.assistant.application.ContextLoadTool;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DefaultCompletionValidator;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DefaultEffectiveSkillResolver;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DefaultRoleSelector;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DefaultSkillSelectionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DefaultTaskComplexityAnalyzer;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DelegatedTaskCoordinator;
import com.xuejiai.aaf.framework.intelligent.assistant.application.EffectiveSkillResolver;
import com.xuejiai.aaf.framework.intelligent.assistant.application.EffectiveToolResolver;
import com.xuejiai.aaf.framework.intelligent.assistant.application.ModelSkillSelectionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.application.PromptAssembler;
import com.xuejiai.aaf.framework.intelligent.assistant.application.RoleSelector;
import com.xuejiai.aaf.framework.intelligent.assistant.application.SkillSelectionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.application.SupportHandoffTool;
import com.xuejiai.aaf.framework.intelligent.assistant.application.TaskComplexityAnalyzer;
import com.xuejiai.aaf.framework.intelligent.assistant.application.TaskIngress;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DecompositionBudget;
import com.xuejiai.aaf.framework.intelligent.assistant.persona.PersonaRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantProvisioningPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskDispatchPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.EffectiveContextPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ExecutionProfileSnapshotPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.NotificationPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.PromptEnvelopePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.RoleDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.SkillDecisionAuditPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.SystemSkillBindingPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskBoardPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskControlPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskRecoveryDispatchPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskRecoveryPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskTransitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiAssistantRoleRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiRoleRepository;
import com.xuejiai.aaf.framework.intelligent.cognition.application.DefaultHybridContextCompressor;
import com.xuejiai.aaf.framework.intelligent.cognition.application.MemoryGovernanceService;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextCompressionSnapshot.Policy;
import com.xuejiai.aaf.framework.intelligent.cognition.port.ContextCompressionPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.L1ContextPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryContextPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.SessionMemoryPort;
import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptInvocationGateway;
import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptTemplateService;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agent.persistence.JpaSkillCatalogAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.spring.AgentScopeInfrastructureAutoConfiguration;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.AssistantRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.AssistantTaskControlRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.ContextSourcePreferenceRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.EffectiveContextManifestRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.ExecutionProfileSnapshotRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaAssistantDefinitionAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaAssistantProvisioningAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaEffectiveContextAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaExecutionProfileSnapshotAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaPromptEnvelopeAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaRoleDefinitionAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaSystemSkillBindingAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaTaskControlAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.PromptEnvelopeRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.SystemSkillBindingRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.ApprovalRecoveryDispatcher;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;

/** Assistant 应用层装配；AgentScope 只通过稳定执行端口进入。 */
@AutoConfiguration
@AutoConfigureAfter({AgentScopeInfrastructureAutoConfiguration.class, AiAutoConfiguration.class})
public class AssistantInfrastructureAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AssistantDefinitionPort.class)
    AssistantDefinitionPort assistantDefinitionPort(
            AssistantRepository assistants,
            PersonaRepository personas,
            AiAssistantRoleRepository bindings,
            AiRoleRepository roles,
            AiModelRepository models) {
        return new JpaAssistantDefinitionAdapter(assistants, personas, bindings, roles, models);
    }

    @Bean
    @ConditionalOnMissingBean(AssistantProvisioningPort.class)
    AssistantProvisioningPort assistantProvisioningPort(
            AssistantRepository assistants, AiAssistantRoleRepository bindings) {
        return new JpaAssistantProvisioningAdapter(assistants, bindings);
    }

    @Bean
    @ConditionalOnMissingBean(RoleDefinitionPort.class)
    RoleDefinitionPort roleDefinitionPort(AiRoleRepository repository) {
        return new JpaRoleDefinitionAdapter(repository);
    }

    @Bean
    @ConditionalOnBean(SkillStore.class)
    @ConditionalOnMissingBean(SystemSkillBindingPort.class)
    SystemSkillBindingPort systemSkillBindingPort(
            SystemSkillBindingRepository repository, SkillStore skillStore) {
        return new JpaSystemSkillBindingAdapter(repository, skillStore);
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
    @ConditionalOnMissingBean(TaskComplexityAnalyzer.class)
    TaskComplexityAnalyzer taskComplexityAnalyzer() {
        return new DefaultTaskComplexityAnalyzer();
    }

    @Bean
    @ConditionalOnMissingBean(TaskControlPort.class)
    TaskControlPort assistantTaskControlPort(
            AssistantTaskControlRepository repository, ConversationLeasePort leases) {
        return new JpaTaskControlAdapter(repository, leases);
    }

    @Bean
    @ConditionalOnMissingBean(SupportHandoffTool.class)
    SupportHandoffTool supportHandoffTool(TaskControlPort tasks, ExecutionEventStorePort events) {
        return new SupportHandoffTool(tasks, events);
    }

    @Bean
    @ConditionalOnBean({SkillCatalogPort.class, SkillReferenceCatalogPort.class})
    @ConditionalOnMissingBean(ContextLoadTool.class)
    ContextLoadTool contextLoadTool(
            SkillCatalogPort skills, SkillReferenceCatalogPort skillReferences) {
        return new ContextLoadTool(skills, skillReferences);
    }

    @Bean
    @ConditionalOnBean(LlmClient.class)
    @ConditionalOnMissingBean(PromptInvocationGateway.class)
    PromptInvocationGateway promptInvocationGateway(LlmClient llmClient) {
        return new PromptInvocationGateway(llmClient);
    }

    @Bean
    @ConditionalOnBean(LlmClient.class)
    @ConditionalOnMissingBean(RoleSelector.class)
    RoleSelector modelRoleSelector(PromptInvocationGateway promptGateway) {
        return new DefaultRoleSelector(promptGateway);
    }

    /** 无 LlmClient 时执行版本化默认 Role 安全策略，不创建模型调用或扩大候选。 */
    @Bean
    @ConditionalOnMissingBean(RoleSelector.class)
    RoleSelector roleSelector() {
        return new DefaultRoleSelector();
    }

    @Bean
    @ConditionalOnMissingBean(ExecutionProfileSnapshotPort.class)
    ExecutionProfileSnapshotPort executionProfileSnapshotPort(
            ExecutionProfileSnapshotRepository repository) {
        return new JpaExecutionProfileSnapshotAdapter(repository);
    }

    @Bean
    @ConditionalOnMissingBean(PromptEnvelopePort.class)
    PromptEnvelopePort promptEnvelopePort(PromptEnvelopeRepository repository) {
        return new JpaPromptEnvelopeAdapter(repository);
    }

    @Bean
    @ConditionalOnBean(LlmClient.class)
    @ConditionalOnMissingBean(SkillSelectionPort.class)
    SkillSelectionPort modelSkillSelectionPort(PromptInvocationGateway promptGateway) {
        return new ModelSkillSelectionPort(promptGateway);
    }

    @Bean
    @ConditionalOnMissingBean(SkillSelectionPort.class)
    SkillSelectionPort defaultSkillSelectionPort() {
        return new DefaultSkillSelectionPort();
    }

    @Bean
    @ConditionalOnMissingBean(DecompositionBudget.class)
    DecompositionBudget decompositionBudget() {
        return DecompositionBudget.defaults();
    }

    @Bean
    @ConditionalOnMissingBean(PromptAssembler.class)
    PromptAssembler promptAssembler(PromptTemplateService promptTemplates) {
        return new PromptAssembler(promptTemplates);
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
    @ConditionalOnBean(LlmClient.class)
    @ConditionalOnMissingBean(ContextCompressionPort.class)
    ContextCompressionPort contextCompressionPort(
            LlmClient llmClient, PromptTemplateService promptTemplates, AiProperties aiProperties) {
        var context = aiProperties.getContext();
        var policy =
                new Policy(
                        DefaultHybridContextCompressor.POLICY_VERSION,
                        Boolean.TRUE.equals(context.getEnabled()),
                        context.getDefaultPolicy(),
                        context.getDefaultContextWindow(),
                        context.getReservedOutputTokens(),
                        context.getFixedPromptBudget(),
                        context.getCompressionTriggerRatio(),
                        context.getLastKeep(),
                        context.getMessageThreshold(),
                        context.getLargeInputCharThreshold(),
                        context.getRulePreviewChars(),
                        Boolean.TRUE.equals(context.getEnableSummary()),
                        context.getSummaryMaxChars(),
                        context.getSummaryTimeoutMs());
        return new DefaultHybridContextCompressor(
                policy, context.getSummaryModelId(), llmClient, promptTemplates);
    }

    @Bean
    @ConditionalOnBean({
        AssistantDefinitionPort.class,
        TaskControlPort.class,
        TaskBoardPort.class,
        AgentExecutionPort.class,
        EffectiveToolResolver.class,
        PromptAssembler.class,
        RoleSelector.class,
        SkillSelectionPort.class,
        ExecutionProfileSnapshotPort.class,
        EffectiveSkillResolver.class,
        SystemSkillBindingPort.class,
        SkillCatalogPort.class,
        EffectiveContextPort.class,
        MemoryContextPort.class,
        L1ContextPort.class,
        ContextCompressionPort.class,
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
            RoleSelector roleSelector,
            SystemSkillBindingPort systemSkillBindings,
            SkillCatalogPort skillCatalog,
            EffectiveSkillResolver effectiveSkillResolver,
            SkillSelectionPort skillSelection,
            EffectiveToolResolver effectiveToolResolver,
            ObjectProvider<SkillDecisionAuditPort> skillDecisionAudits,
            PromptAssembler promptAssembler,
            ExecutionProfileSnapshotPort executionProfiles,
            EffectiveContextPort effectiveContexts,
            L1ContextPort cognitionContexts,
            ContextCompressionPort contextCompression,
            MemoryGovernanceService memoryGovernance,
            AgentExecutionPort agentExecution,
            CapabilityRouter models,
            CompletionValidator completionValidator,
            ExecutionEventStorePort eventStore,
            TaskRecoveryPort recoveries,
            ObjectProvider<SessionMemoryPort> sessionMemories,
            AiProperties aiProperties) {
        return new AssistantApplicationService(
                definitions,
                tasks,
                taskBoards,
                roleSelector,
                systemSkillBindings,
                skillCatalog,
                effectiveSkillResolver,
                skillSelection,
                effectiveToolResolver,
                skillDecisionAudits.getIfAvailable(SkillDecisionAuditPort::noop),
                promptAssembler,
                executionProfiles,
                effectiveContexts,
                cognitionContexts,
                contextCompression,
                memoryGovernance,
                agentExecution,
                models,
                completionValidator,
                eventStore,
                recoveries,
                sessionMemories,
                aiProperties.getContext().getDefaultContextWindow());
    }

    @Bean
    @ConditionalOnMissingBean(TaskRecoveryDispatchPort.class)
    TaskRecoveryDispatchPort taskRecoveryDispatchPort(
            TaskRecoveryPort recoveries, AssistantCommandPort commands) {
        return new ApprovalRecoveryDispatcher(recoveries, commands);
    }

    @Bean
    @ConditionalOnBean({DelegatedTaskPort.class, DelegatedTaskDispatchPort.class})
    @ConditionalOnMissingBean(TaskIngress.class)
    TaskIngress taskIngress(DelegatedTaskPort tasks, DelegatedTaskDispatchPort dispatchSignals) {
        return new TaskIngress(tasks, dispatchSignals);
    }

    @Bean
    @ConditionalOnBean({
        DelegatedTaskPort.class,
        TaskTransitionPort.class,
        TaskIngress.class,
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
            TaskTransitionPort transitions,
            TaskIngress taskIngress,
            TaskBoardPort boards,
            ConversationLeasePort leases,
            AssistantCommandPort assistants,
            AgentExecutionPort agentExecutions,
            NotificationPort notifications,
            DelegatedTaskDispatchPort dispatchSignals,
            AgentTaskRuntime agentTaskRuntime,
            DecompositionBudget decompositionBudget,
            Environment environment) {
        var leaseTtl =
                Duration.ofSeconds(
                        environment.getProperty(
                                "aaf.assistant.delegated.lease-seconds", Long.class, 60L));
        return new DelegatedTaskCoordinator(
                tasks,
                transitions,
                taskIngress,
                boards,
                leases,
                assistants,
                agentExecutions,
                notifications,
                dispatchSignals,
                agentTaskRuntime,
                decompositionBudget,
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
}
