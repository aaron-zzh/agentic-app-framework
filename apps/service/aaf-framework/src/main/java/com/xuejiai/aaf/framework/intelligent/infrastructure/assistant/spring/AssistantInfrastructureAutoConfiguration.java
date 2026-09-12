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
import com.xuejiai.aaf.framework.intelligent.assistant.application.AmendTaskTool;
import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantApplicationService;
import com.xuejiai.aaf.framework.intelligent.assistant.application.CancelTaskTool;
import com.xuejiai.aaf.framework.intelligent.assistant.application.CompletionValidator;
import com.xuejiai.aaf.framework.intelligent.assistant.application.ContextLoadTool;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DefaultCompletionValidator;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DefaultEffectiveSkillResolver;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DefaultRoleSelector;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DefaultSkillSelectionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.application.EffectiveSkillResolver;
import com.xuejiai.aaf.framework.intelligent.assistant.application.EffectiveToolResolver;
import com.xuejiai.aaf.framework.intelligent.assistant.application.HandBackTaskTool;
import com.xuejiai.aaf.framework.intelligent.assistant.application.InspectTasksTool;
import com.xuejiai.aaf.framework.intelligent.assistant.application.ModelSkillSelectionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.application.PauseTaskTool;
import com.xuejiai.aaf.framework.intelligent.assistant.application.PromoteDirectTaskTool;
import com.xuejiai.aaf.framework.intelligent.assistant.application.PromptAssembler;
import com.xuejiai.aaf.framework.intelligent.assistant.application.RenderUiBlockTool;
import com.xuejiai.aaf.framework.intelligent.assistant.application.ReportExecutorStepTool;
import com.xuejiai.aaf.framework.intelligent.assistant.application.RequestClarificationTool;
import com.xuejiai.aaf.framework.intelligent.assistant.application.ResumeTaskTool;
import com.xuejiai.aaf.framework.intelligent.assistant.application.RoleSelector;
import com.xuejiai.aaf.framework.intelligent.assistant.application.SkillSelectionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.application.SubmitCoordinationPlanTool;
import com.xuejiai.aaf.framework.intelligent.assistant.application.SubmitExecutorPlanTool;
import com.xuejiai.aaf.framework.intelligent.assistant.application.TakeOverTaskTool;
import com.xuejiai.aaf.framework.intelligent.assistant.application.TaskCommandService;
import com.xuejiai.aaf.framework.intelligent.assistant.application.TaskIngress;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DecompositionBudget;
import com.xuejiai.aaf.framework.intelligent.assistant.persona.PersonaRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantProvisioningPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.EffectiveContextPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ExecutionProfileSnapshotPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HitlTransitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.RoleDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.SkillDecisionAuditPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.SystemSkillBindingPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskDispatchSignalPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskMaterializationPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskPlanPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskQueryPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskRecoveryDispatchPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskRecoveryPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskUnitOfWork;
import com.xuejiai.aaf.framework.intelligent.assistant.port.plan.ExecutorPlanPort;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiAssistantRoleRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiRoleRepository;
import com.xuejiai.aaf.framework.intelligent.cognition.application.DefaultHybridContextCompressor;
import com.xuejiai.aaf.framework.intelligent.cognition.application.MemoryGovernanceService;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextCompressionSnapshot.Policy;
import com.xuejiai.aaf.framework.intelligent.cognition.port.ContextCompressionPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.L1ContextPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryContextPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.SessionMemoryPort;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptInvocationGateway;
import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptTemplateService;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agent.persistence.JpaSkillCatalogAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.spring.AgentScopeInfrastructureAutoConfiguration;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.AssistantRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.ContextSourcePreferenceRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.EffectiveContextManifestRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.ExecutionProfileSnapshotRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaAssistantDefinitionAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaAssistantProvisioningAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaEffectiveContextAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaExecutionProfileSnapshotAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaRoleDefinitionAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaSystemSkillBindingAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.SystemSkillBindingRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.plan.ExecutorPlanRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.plan.ExecutorPlanStepRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.plan.JpaExecutorPlanAdapter;
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

    /**
     * 计划提交后直接批准，不设独立审批关卡（ADR-006「决策推翻」2026-09-01）——风险控制交给协调者派发子节点时的既有审批点，
     * 与步骤执行阶段具体工具调用前的既有授权链路，不为"提交步骤列表"这个动作重复建设审批机制。
     */
    @Bean
    @ConditionalOnBean({ExecutorPlanPort.class, ExecutionEventStorePort.class})
    @ConditionalOnMissingBean(SubmitExecutorPlanTool.class)
    SubmitExecutorPlanTool submitExecutorPlanTool(
            ExecutorPlanPort plans, ExecutionEventStorePort events) {
        return new SubmitExecutorPlanTool(plans, events, Clock.systemUTC());
    }

    /**
     * 单次 execution 内唯一允许模型上报步骤边界的工具（AAF-107 #10705）：{@code report_executor_step} 让模型在单次 execution
     * 内自由推进已提交计划的全部步骤，{@code ExecutorPlanStep.Status} 要有真实数据必须由模型自己 显式上报——与 {@code
     * submitExecutorPlanTool} 同为"模型主动上报"模式，注册条件也保持一致。
     */
    @Bean
    @ConditionalOnBean({ExecutorPlanPort.class, ExecutionEventStorePort.class})
    @ConditionalOnMissingBean(ReportExecutorStepTool.class)
    ReportExecutorStepTool reportExecutorStepTool(
            ExecutorPlanPort plans, ExecutionEventStorePort events) {
        return new ReportExecutorStepTool(plans, events, Clock.systemUTC());
    }

    /**
     * 协调者 execution 内可用的写工具：提交协调计划，替代手工 JSON 文本解析（迁移自 {@code
     * TaskCommandService.decodeAndValidatePlan}，业务规则原样保留）。是否规划不再是建板时的静态 判定（AAF-107 选项 B
     * 架构改造，2026-09-02，{@code PlanRequirementPolicy} 已随之删除），改为运行时查询是否存在 活跃 {@code
     * ExecutorPlan}，与本工具无关。
     */
    @Bean
    @ConditionalOnBean(TaskPlanPort.class)
    @ConditionalOnMissingBean(SubmitCoordinationPlanTool.class)
    SubmitCoordinationPlanTool submitTaskPlanDraftTool(
            TaskPlanPort boards, DecompositionBudget decompositionBudget) {
        return new SubmitCoordinationPlanTool(boards, decompositionBudget);
    }

    @Bean
    @ConditionalOnBean({SkillCatalogPort.class, SkillReferenceCatalogPort.class})
    @ConditionalOnMissingBean(ContextLoadTool.class)
    ContextLoadTool contextLoadTool(
            SkillCatalogPort skills, SkillReferenceCatalogPort skillReferences) {
        return new ContextLoadTool(skills, skillReferences);
    }

    /** 结构化展示卡片工具（AAF-114 官方模式改造）；无外部依赖，始终可注册。 */
    @Bean
    @ConditionalOnMissingBean(RenderUiBlockTool.class)
    RenderUiBlockTool renderUiBlockTool() {
        return new RenderUiBlockTool();
    }

    /**
     * 非自主 L0 逻辑调用入口已迁移至 AgentScopeInfrastructureAutoConfiguration.promptInvocationGateway（依
     * ADR-007），不再由本配置类基于 LlmClient 生产。
     */
    @Bean
    @ConditionalOnBean(PromptInvocationGateway.class)
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
    @ConditionalOnMissingBean(ExecutorPlanPort.class)
    ExecutorPlanPort executorPlanPort(
            ExecutorPlanRepository plans, ExecutorPlanStepRepository steps) {
        return new JpaExecutorPlanAdapter(plans, steps);
    }

    @Bean
    @ConditionalOnBean(PromptInvocationGateway.class)
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
    @ConditionalOnBean(PromptInvocationGateway.class)
    @ConditionalOnMissingBean(ContextCompressionPort.class)
    ContextCompressionPort contextCompressionPort(
            PromptInvocationGateway promptGateway,
            PromptTemplateService promptTemplates,
            AiProperties aiProperties) {
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
                policy, context.getSummaryModelId(), promptGateway, promptTemplates);
    }

    @Bean
    @ConditionalOnBean({
        AssistantDefinitionPort.class,
        TaskUnitOfWork.class,
        TaskMaterializationPort.class,
        TaskPlanPort.class,
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
            TaskUnitOfWork tasks,
            TaskMaterializationPort materializations,
            TaskPlanPort taskBoards,
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
                materializations,
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
    @ConditionalOnBean(TaskUnitOfWork.class)
    @ConditionalOnMissingBean(TaskIngress.class)
    TaskIngress taskIngress(TaskUnitOfWork tasks) {
        return new TaskIngress(tasks);
    }

    @Bean
    @ConditionalOnBean({
        TaskUnitOfWork.class,
        TaskIngress.class,
        TaskMaterializationPort.class,
        ExecutionProfileSnapshotPort.class,
        HitlTransitionPort.class,
        ConversationLeasePort.class,
        AssistantCommandPort.class,
        AgentExecutionPort.class,
        TaskDispatchSignalPort.class
    })
    @ConditionalOnMissingBean
    TaskCommandService taskCommandService(
            TaskUnitOfWork tasks,
            TaskIngress taskIngress,
            TaskMaterializationPort materializations,
            ExecutionProfileSnapshotPort executionProfiles,
            HitlTransitionPort transitions,
            ConversationLeasePort leases,
            AssistantCommandPort assistants,
            AgentExecutionPort agentExecutions,
            TaskDispatchSignalPort dispatchSignals,
            Environment environment) {
        var leaseTtl =
                Duration.ofSeconds(
                        environment.getProperty(
                                "aaf.assistant.delegated.lease-seconds", Long.class, 60L));
        var pauseAckTimeout =
                Duration.ofSeconds(
                        environment.getProperty(
                                "aaf.assistant.pause-ack-timeout-seconds", Long.class, 30L));
        return new TaskCommandService(
                tasks,
                taskIngress,
                materializations,
                executionProfiles,
                transitions,
                leases,
                assistants,
                agentExecutions,
                dispatchSignals,
                Clock.systemUTC(),
                leaseTtl,
                pauseAckTimeout);
    }

    @Bean
    @ConditionalOnBean({TaskQueryPort.class, ExecutorPlanPort.class})
    @ConditionalOnMissingBean(InspectTasksTool.class)
    InspectTasksTool inspectTasksTool(TaskQueryPort taskQueries, ExecutorPlanPort executorPlans) {
        return new InspectTasksTool(taskQueries, executorPlans);
    }

    @Bean
    @ConditionalOnBean(TaskCommandService.class)
    @ConditionalOnMissingBean(PromoteDirectTaskTool.class)
    PromoteDirectTaskTool promoteDirectTaskTool(TaskCommandService taskCommands) {
        return new PromoteDirectTaskTool(taskCommands);
    }

    @Bean
    @ConditionalOnBean(HitlTransitionPort.class)
    @ConditionalOnMissingBean(RequestClarificationTool.class)
    RequestClarificationTool requestClarificationTool(HitlTransitionPort transitions) {
        return new RequestClarificationTool(transitions, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnBean(TaskCommandService.class)
    @ConditionalOnMissingBean(AmendTaskTool.class)
    AmendTaskTool amendTaskTool(TaskCommandService taskCommands) {
        return new AmendTaskTool(taskCommands, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnBean(TaskCommandService.class)
    @ConditionalOnMissingBean(PauseTaskTool.class)
    PauseTaskTool pauseTaskTool(TaskCommandService taskCommands) {
        return new PauseTaskTool(taskCommands);
    }

    @Bean
    @ConditionalOnBean(TaskCommandService.class)
    @ConditionalOnMissingBean(ResumeTaskTool.class)
    ResumeTaskTool resumeTaskTool(TaskCommandService taskCommands) {
        return new ResumeTaskTool(taskCommands);
    }

    @Bean
    @ConditionalOnBean(TaskCommandService.class)
    @ConditionalOnMissingBean(TakeOverTaskTool.class)
    TakeOverTaskTool takeOverTaskTool(TaskCommandService taskCommands) {
        return new TakeOverTaskTool(taskCommands);
    }

    @Bean
    @ConditionalOnBean(TaskCommandService.class)
    @ConditionalOnMissingBean(HandBackTaskTool.class)
    HandBackTaskTool handBackTaskTool(TaskCommandService taskCommands) {
        return new HandBackTaskTool(taskCommands);
    }

    @Bean
    @ConditionalOnBean(TaskCommandService.class)
    @ConditionalOnMissingBean(CancelTaskTool.class)
    CancelTaskTool cancelTaskTool(TaskCommandService taskCommands) {
        return new CancelTaskTool(taskCommands);
    }

    @Bean
    @ConditionalOnBean(TaskCommandService.class)
    @ConditionalOnMissingBean
    TaskDispatchScheduler taskDispatchScheduler(
            TaskCommandService coordinator,
            AgentTaskRuntime agentTaskRuntime,
            Environment environment) {
        var defaultWorker =
                ManagementFactory.getRuntimeMXBean().getName() + "-" + UUID.randomUUID();
        var workerId = environment.getProperty("aaf.assistant.delegated.worker-id", defaultWorker);
        return new TaskDispatchScheduler(coordinator, agentTaskRuntime, workerId);
    }

    @Bean
    @ConditionalOnBean({TaskCommandService.class, AgentTaskRuntime.class})
    SmartInitializingSingleton delegatedTaskAgentTaskRegistration(
            TaskCommandService coordinator, AgentTaskRuntime agentTaskRuntime) {
        return () -> agentTaskRuntime.register(new TaskDispatchAgentTaskAdapter(coordinator));
    }
}
