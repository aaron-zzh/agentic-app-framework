package com.xuejiai.aaf.framework.intelligent.infrastructure.governance.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingProperties;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.intelligent.agent.application.DefaultToolGateway;
import com.xuejiai.aaf.framework.intelligent.agent.application.DefaultToolParameterPolicy;
import com.xuejiai.aaf.framework.intelligent.agent.port.AuthorizationGrantPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ConnectorActionPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.CredentialVaultPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.TokenMeteringPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolGatewayPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolParameterPolicyPort;
import com.xuejiai.aaf.framework.intelligent.ai.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.intelligent.assistant.application.PersistentHitlCoordinator;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HitlCoordinatorPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HumanApprovalPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskControlPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskRecoveryPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskResumeSignalPort;
import com.xuejiai.aaf.framework.intelligent.cognition.application.DefaultMemoryContextCollaborator;
import com.xuejiai.aaf.framework.intelligent.cognition.application.MemoryGovernanceService;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryContextPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryGovernancePort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryRecallPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryWritePort;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelManagementService;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.spring.AgentRuntimePortAutoConfiguration;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.spring.AgentScopeInfrastructureAutoConfiguration;
import com.xuejiai.aaf.framework.intelligent.infrastructure.cognition.KnowledgeEmbeddingAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.cognition.RuleBasedMemoryGovernanceAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.cognition.persistence.CognitionMemoryRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.cognition.persistence.JpaCognitionMemoryAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.RegistryConnectorActionAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.SpringTaskResumeSignalAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.AuthorizationGrantRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.ConnectorActionExecutionRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.CredentialHandleRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.HitlRecoveryRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.HumanApprovalRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.JpaAuthorizationGrantAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.JpaCredentialVaultAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.JpaHumanApprovalAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.JpaTaskRecoveryAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.TaskRecoveryCommandRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.metering.persistence.JpaTokenMeteringAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.trace.persistence.ExecutionEventRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.trace.persistence.JpaExecutionEventStoreAdapter;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;

/** P3 Cognition、授权、HITL、连接器、计量与轨迹唯一生产接线。 */
@AutoConfiguration
@AutoConfigureAfter(AgentRuntimePortAutoConfiguration.class)
@AutoConfigureBefore(AgentScopeInfrastructureAutoConfiguration.class)
public class IntelligentGovernanceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ExecutionEventStorePort.class)
    JpaExecutionEventStoreAdapter executionEventStore(ExecutionEventRepository repository) {
        return new JpaExecutionEventStoreAdapter(repository);
    }

    @Bean
    @ConditionalOnMissingBean(TokenMeteringPort.class)
    TokenMeteringPort tokenMeteringPort(
            ModelManagementService models, AiCreditGuard creditGuard) {
        return new JpaTokenMeteringAdapter(models, creditGuard);
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
    MemoryContextPort memoryContextPort(MemoryRecallPort recall) {
        return new DefaultMemoryContextCollaborator(recall);
    }

    @Bean
    MemoryGovernanceService memoryGovernanceService(
            MemoryGovernancePort governance, MemoryWritePort writer) {
        return new MemoryGovernanceService(governance, writer);
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
            ConnectorActionExecutionRepository executions) {
        return new RegistryConnectorActionAdapter(registry, credentials, executions);
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
            ExecutionEventStorePort events) {
        return new PersistentHitlCoordinator(
                approvals, grants, credentials, tasks, recoveries, resumeSignals, events);
    }

    @Bean
    @ConditionalOnMissingBean(ToolGatewayPort.class)
    ToolGatewayPort toolGatewayPort(
            AuthorizationGrantPort grants,
            ToolParameterPolicyPort parameterPolicy,
            HitlCoordinatorPort hitl,
            ToolInvocationPort localTools,
            ConnectorActionPort connectors) {
        return new DefaultToolGateway(grants, parameterPolicy, hitl, localTools, connectors);
    }
}
