package com.xuejiai.aaf.framework.intelligent.infrastructure.governance.spring;

import java.nio.file.Path;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingProperties;
import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchService;
import com.xuejiai.aaf.framework.engine.lease.LeaseAutoConfiguration;
import com.xuejiai.aaf.framework.engine.lease.RedisDistributedLeaseAdapter;
import com.xuejiai.aaf.framework.engine.memory.AtomMemoryEngine;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.intelligent.agent.application.DefaultToolGateway;
import com.xuejiai.aaf.framework.intelligent.agent.application.DefaultToolParameterPolicy;
import com.xuejiai.aaf.framework.intelligent.agent.port.AuthorizationGrantPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ConnectorActionPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.CredentialVaultPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.InvocationReceiptPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.McpPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.SandboxPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ScopedFileSystemPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.TokenMeteringPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolGatewayPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolParameterPolicyPort;
import com.xuejiai.aaf.framework.intelligent.ai.chat.AiProperties;
import com.xuejiai.aaf.framework.intelligent.ai.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.intelligent.assistant.application.PersistentHitlCoordinator;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskDispatchPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HitlCoordinatorPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HumanApprovalPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.NotificationPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.PromptEnvelopePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskBoardPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskControlPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskRecoveryPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskResumeSignalPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskTransitionPort;
import com.xuejiai.aaf.framework.intelligent.cognition.application.DefaultL1ContextCollaborator;
import com.xuejiai.aaf.framework.intelligent.cognition.application.DefaultMemoryContextCollaborator;
import com.xuejiai.aaf.framework.intelligent.cognition.application.DefaultMemoryRetrievalPort;
import com.xuejiai.aaf.framework.intelligent.cognition.application.DefaultSessionContextCompressor;
import com.xuejiai.aaf.framework.intelligent.cognition.application.DefaultUnifiedRetrievalPort;
import com.xuejiai.aaf.framework.intelligent.cognition.application.MemoryGovernanceService;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.MemoryRerankerService;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.ShortTermMemoryService;
import com.xuejiai.aaf.framework.intelligent.cognition.port.ConversationHistoryPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.L1ContextPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryContextPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryGovernancePort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryRecallPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryRetrievalPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryWritePort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.SessionContextCompressionPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.SessionMemoryPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.UnifiedRetrievalPort;
import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelManagementService;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.spring.AgentRuntimePortAutoConfiguration;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.spring.AgentScopeInfrastructureAutoConfiguration;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.lease.RedisConversationLeaseAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.ClarificationRequestRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.DelegatedTaskRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaDelegatedTaskAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaNotificationOutboxAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaPromptEnvelopeAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaTaskBoardAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.JpaTaskTransitionAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.PromptEnvelopeRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.TaskBoardRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.TaskInputRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.TaskNotificationOutboxRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.TaskTransitionOutboxRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.spring.SpringDelegatedTaskDispatchAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.cognition.KnowledgeEmbeddingAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.cognition.RuleBasedMemoryGovernanceAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.cognition.memory.RedisSessionMemoryAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.cognition.persistence.CognitionMemoryRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.cognition.persistence.JpaCognitionMemoryAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.GovernedMcpAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.GovernedSandboxAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.LocalScopedFileSystemAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.RegistryConnectorActionAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.SpringTaskResumeSignalAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.AuthorizationGrantRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.CredentialHandleRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.HitlRecoveryRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.HumanApprovalRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.JpaAuthorizationGrantAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.JpaCredentialVaultAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.JpaHumanApprovalAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.JpaInvocationReceiptAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.JpaTaskRecoveryAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.TaskRecoveryCommandRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.ToolInvocationReceiptRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.metering.persistence.JpaTokenMeteringAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.trace.persistence.ExecutionEventRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.trace.persistence.JpaExecutionEventStoreAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.trace.persistence.SynchronousExecutionEventWriter;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;

import io.micrometer.core.instrument.MeterRegistry;

/** Cognition 与 P3/P4 治理基础设施的唯一生产接线。 */
@AutoConfiguration
@AutoConfigureAfter({LeaseAutoConfiguration.class, AgentRuntimePortAutoConfiguration.class})
@AutoConfigureBefore(AgentScopeInfrastructureAutoConfiguration.class)
public class IntelligentGovernanceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ConversationLeasePort.class)
    ConversationLeasePort conversationLeasePort(RedisDistributedLeaseAdapter leases) {
        return new RedisConversationLeaseAdapter(leases);
    }

    @Bean
    @ConditionalOnMissingBean(SynchronousExecutionEventWriter.class)
    SynchronousExecutionEventWriter synchronousExecutionEventWriter(
            ExecutionEventRepository repository) {
        return new SynchronousExecutionEventWriter(repository);
    }

    @Bean
    @ConditionalOnMissingBean(DelegatedTaskPort.class)
    DelegatedTaskPort delegatedTaskPort(
            DelegatedTaskRepository repository,
            TaskBoardRepository taskBoards,
            TaskInputRepository inputs,
            ConversationLeasePort leases) {
        return new JpaDelegatedTaskAdapter(repository, taskBoards, inputs, leases);
    }

    @Bean
    @ConditionalOnMissingBean(TaskTransitionPort.class)
    TaskTransitionPort taskTransitionPort(
            DelegatedTaskRepository tasks,
            TaskBoardRepository boards,
            HumanApprovalRepository approvals,
            AuthorizationGrantRepository grants,
            ClarificationRequestRepository clarifications,
            TaskInputRepository inputs,
            SynchronousExecutionEventWriter eventWriter,
            TaskTransitionOutboxRepository outbox,
            ApplicationEventPublisher publisher,
            ConversationLeasePort leases,
            MeterRegistry meters) {
        return new JpaTaskTransitionAdapter(
                tasks,
                boards,
                approvals,
                grants,
                clarifications,
                inputs,
                eventWriter,
                outbox,
                publisher,
                leases,
                meters);
    }

    @Bean
    @ConditionalOnMissingBean(TaskBoardPort.class)
    TaskBoardPort taskBoardPort(TaskBoardRepository repository, ConversationLeasePort leases) {
        return new JpaTaskBoardAdapter(repository, leases);
    }

    @Bean
    @ConditionalOnMissingBean(NotificationPort.class)
    NotificationPort notificationPort(
            TaskNotificationOutboxRepository repository, ApplicationEventPublisher publisher) {
        return new JpaNotificationOutboxAdapter(repository, publisher);
    }

    @Bean
    @ConditionalOnMissingBean(InvocationReceiptPort.class)
    InvocationReceiptPort invocationReceiptPort(
            ToolInvocationReceiptRepository repository,
            ConversationLeasePort leases,
            DelegatedTaskPort delegatedTasks) {
        return new JpaInvocationReceiptAdapter(repository, leases, delegatedTasks);
    }

    @Bean
    @ConditionalOnMissingBean(ExecutionEventStorePort.class)
    JpaExecutionEventStoreAdapter executionEventStore(
            ExecutionEventRepository repository,
            SynchronousExecutionEventWriter writer,
            ConversationLeasePort leases,
            DelegatedTaskPort delegatedTasks) {
        return new JpaExecutionEventStoreAdapter(repository, writer, leases, delegatedTasks);
    }

    @Bean
    @ConditionalOnMissingBean(TokenMeteringPort.class)
    TokenMeteringPort tokenMeteringPort(
            ModelManagementService models,
            AiCreditGuard creditGuard,
            DelegatedTaskPort delegatedTasks,
            ConversationLeasePort leases) {
        return new JpaTokenMeteringAdapter(models, creditGuard, delegatedTasks, leases);
    }

    @Bean
    @ConditionalOnMissingBean(AuthorizationGrantPort.class)
    AuthorizationGrantPort authorizationGrantPort(AuthorizationGrantRepository repository) {
        return new JpaAuthorizationGrantAdapter(repository);
    }

    @Bean
    @ConditionalOnMissingBean(CredentialVaultPort.class)
    CredentialVaultPort credentialVaultPort(CredentialHandleRepository repository) {
        return new JpaCredentialVaultAdapter(repository);
    }

    @Bean
    @ConditionalOnMissingBean(HumanApprovalPort.class)
    HumanApprovalPort humanApprovalPort(HumanApprovalRepository repository) {
        return new JpaHumanApprovalAdapter(repository);
    }

    @Bean
    @ConditionalOnMissingBean(TaskRecoveryPort.class)
    TaskRecoveryPort taskRecoveryPort(
            TaskRecoveryCommandRepository commands, HitlRecoveryRepository recoveries) {
        return new JpaTaskRecoveryAdapter(commands, recoveries);
    }

    @Bean
    @ConditionalOnMissingBean(DelegatedTaskDispatchPort.class)
    DelegatedTaskDispatchPort delegatedTaskDispatchPort(ApplicationEventPublisher publisher) {
        return new SpringDelegatedTaskDispatchAdapter(publisher);
    }

    /**
     * PromptEnvelope 端口须在 AgentScopeInfrastructureAutoConfiguration 之前可用： 该配置类的
     * promptEnvelopeCaptureMiddleware Bean 依赖此端口，而 AgentScopeInfrastructureAutoConfiguration
     * 早于（原属地）AssistantInfrastructureAutoConfiguration
     * 装配，定义在此处以匹配 @AutoConfigureBefore(AgentScopeInfrastructureAutoConfiguration.class) 顺序。
     */
    @Bean
    @ConditionalOnMissingBean(PromptEnvelopePort.class)
    PromptEnvelopePort promptEnvelopePort(PromptEnvelopeRepository repository) {
        return new JpaPromptEnvelopeAdapter(repository);
    }

    @Bean
    @ConditionalOnMissingBean(TaskResumeSignalPort.class)
    TaskResumeSignalPort taskResumeSignalPort(ApplicationEventPublisher publisher) {
        return new SpringTaskResumeSignalAdapter(publisher);
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingService.class)
    EmbeddingService cognitionEmbeddingService(
            com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService delegate,
            EmbeddingProperties properties) {
        return new KnowledgeEmbeddingAdapter(delegate, properties);
    }

    @Bean
    JpaCognitionMemoryAdapter cognitionMemoryPort(
            CognitionMemoryRepository repository,
            EmbeddingService embeddings,
            EmbeddingProperties properties) {
        return new JpaCognitionMemoryAdapter(repository, embeddings, properties.model());
    }

    @Bean
    MemoryGovernancePort memoryGovernancePort(MemoryRecallPort recall) {
        return new RuleBasedMemoryGovernanceAdapter(recall);
    }

    @Bean
    @ConditionalOnBean(LlmClient.class)
    @ConditionalOnMissingBean(SessionContextCompressionPort.class)
    SessionContextCompressionPort sessionContextCompressionPort(
            LlmClient llmClient, AiProperties aiProperties) {
        var config = aiProperties.getSessionSummary();
        return new DefaultSessionContextCompressor(
                llmClient, config.getScene(), config.getTimeoutMs(), config.getMaxChars());
    }

    @Bean
    @ConditionalOnBean(ShortTermMemoryService.class)
    @ConditionalOnMissingBean(SessionMemoryPort.class)
    SessionMemoryPort sessionMemoryPort(
            ShortTermMemoryService shortTermMemories,
            AiProperties aiProperties,
            ObjectProvider<SessionContextCompressionPort> compressor,
            ObjectProvider<ConversationHistoryPort> history) {
        var enabled = Boolean.TRUE.equals(aiProperties.getSessionSummary().getEnabled());
        return new RedisSessionMemoryAdapter(
                shortTermMemories,
                enabled ? compressor.getIfAvailable() : null,
                history.getIfAvailable());
    }

    @Bean
    MemoryContextPort memoryContextPort(
            MemoryRecallPort recall, ObjectProvider<SessionMemoryPort> sessions) {
        return new DefaultMemoryContextCollaborator(recall, sessions.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(MemoryRetrievalPort.class)
    MemoryRetrievalPort memoryRetrievalPort(AtomMemoryEngine atomMemoryEngine) {
        return new DefaultMemoryRetrievalPort(atomMemoryEngine);
    }

    @Bean
    @ConditionalOnBean({MemoryRetrievalPort.class, HybridSearchService.class})
    @ConditionalOnMissingBean(UnifiedRetrievalPort.class)
    UnifiedRetrievalPort unifiedRetrievalPort(
            MemoryRetrievalPort memoryRetrieval,
            HybridSearchService knowledgeSearch,
            com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService embeddingService,
            MemoryRerankerService reranker) {
        return new DefaultUnifiedRetrievalPort(
                memoryRetrieval, knowledgeSearch, embeddingService, reranker);
    }

    @Bean
    MemoryGovernanceService memoryGovernanceService(
            MemoryGovernancePort governance, MemoryWritePort writer) {
        return new MemoryGovernanceService(governance, writer);
    }

    @Bean
    @ConditionalOnBean(UnifiedRetrievalPort.class)
    @ConditionalOnMissingBean(L1ContextPort.class)
    L1ContextPort l1ContextPort(
            UnifiedRetrievalPort unifiedRetrieval, ObjectProvider<SessionMemoryPort> sessions) {
        return new DefaultL1ContextCollaborator(unifiedRetrieval, sessions.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(ToolParameterPolicyPort.class)
    ToolParameterPolicyPort toolParameterPolicyPort() {
        return new DefaultToolParameterPolicy();
    }

    @Bean
    @ConditionalOnMissingBean(ConnectorActionPort.class)
    ConnectorActionPort connectorActionPort(
            ToolRegistry registry,
            CredentialVaultPort credentials,
            ConversationLeasePort leases,
            DelegatedTaskPort delegatedTasks) {
        return new RegistryConnectorActionAdapter(registry, credentials, leases, delegatedTasks);
    }

    @Bean
    @ConditionalOnMissingBean(ScopedFileSystemPort.class)
    ScopedFileSystemPort scopedFileSystemPort(
            Environment environment,
            ConversationLeasePort leases,
            DelegatedTaskPort delegatedTasks) {
        return new LocalScopedFileSystemAdapter(workspaceRoot(environment), leases, delegatedTasks);
    }

    @Bean
    @ConditionalOnMissingBean(SandboxPort.class)
    SandboxPort sandboxPort(
            Environment environment,
            ConversationLeasePort leases,
            DelegatedTaskPort delegatedTasks) {
        return new GovernedSandboxAdapter(workspaceRoot(environment), leases, delegatedTasks);
    }

    @Bean
    @ConditionalOnMissingBean(McpPort.class)
    McpPort mcpPort(
            ConnectorActionPort connectors,
            ConversationLeasePort leases,
            DelegatedTaskPort delegatedTasks,
            InvocationReceiptPort receipts) {
        return new GovernedMcpAdapter(connectors, leases, delegatedTasks, receipts);
    }

    @Bean
    @ConditionalOnMissingBean(HitlCoordinatorPort.class)
    HitlCoordinatorPort hitlCoordinatorPort(
            HumanApprovalPort approvals,
            AuthorizationGrantPort grants,
            CredentialVaultPort credentials,
            TaskControlPort tasks,
            TaskRecoveryPort recoveries,
            TaskResumeSignalPort resumeSignals,
            ExecutionEventStorePort events,
            TaskTransitionPort transitions,
            DelegatedTaskDispatchPort delegatedDispatch,
            ConversationLeasePort leases) {
        return new PersistentHitlCoordinator(
                approvals,
                grants,
                credentials,
                tasks,
                recoveries,
                resumeSignals,
                events,
                transitions,
                delegatedDispatch,
                leases);
    }

    @Bean
    @ConditionalOnMissingBean(ToolGatewayPort.class)
    ToolGatewayPort toolGatewayPort(
            AuthorizationGrantPort grants,
            ToolParameterPolicyPort parameterPolicy,
            TaskTransitionPort transitions,
            ToolInvocationPort localTools,
            ConnectorActionPort connectors,
            ConversationLeasePort leases,
            DelegatedTaskPort delegatedTasks,
            InvocationReceiptPort receipts) {
        return new DefaultToolGateway(
                grants,
                parameterPolicy,
                transitions,
                localTools,
                connectors,
                leases,
                delegatedTasks,
                receipts);
    }

    private static Path workspaceRoot(Environment environment) {
        return Path.of(
                environment.getProperty(
                        "aaf.assistant.delegated.workspace-root",
                        Path.of(System.getProperty("java.io.tmpdir"), "aaf-delegated-workspace")
                                .toString()));
    }
}
