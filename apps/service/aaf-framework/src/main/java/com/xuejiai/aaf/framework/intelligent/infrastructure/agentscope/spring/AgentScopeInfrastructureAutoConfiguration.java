package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.spring;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.xuejiai.aaf.framework.intelligent.agent.port.AgentDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentExecutionPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.TokenMeteringPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolCatalogPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolGatewayPort;
import com.xuejiai.aaf.framework.intelligent.ai.chat.AiAutoConfiguration;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DefaultEffectiveToolResolver;
import com.xuejiai.aaf.framework.intelligent.assistant.application.EffectiveToolResolver;
import com.xuejiai.aaf.framework.intelligent.assistant.port.PromptEnvelopePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskUnitOfWork;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptInvocationGateway;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.compiler.AgentScopeSpecCompiler;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.execution.HarnessAgentExecutionAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeEventMapper;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeMessageMapper;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeRuntimeContextMapper;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.middleware.AgentScopeTokenMeteringObserver;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.middleware.PromptEnvelopeCaptureMiddleware;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.model.AgentScopeModelResolver;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.model.L0ReActAgentFactory;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.state.DispatchGuardedAgentStateStore;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.state.SpringRedisClientAdapter;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool.AgentScopeToolkitFactory;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool.ToolResultEvidenceStore;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;

import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import reactor.core.scheduler.Schedulers;

/**
 * Core + Agent 唯一 AgentScope 基础设施接线。
 *
 * <p>六个必需端口齐备时才装配，缺任一端口整个 AgentScope 运行时不生效。
 */
@AutoConfiguration
@AutoConfigureAfter({DataRedisAutoConfiguration.class, AiAutoConfiguration.class})
@ConditionalOnBean({
    AgentDefinitionPort.class,
    ToolCatalogPort.class,
    ToolGatewayPort.class,
    TokenMeteringPort.class,
    ExecutionEventStorePort.class,
    PromptEnvelopePort.class
})
public class AgentScopeInfrastructureAutoConfiguration {

    /** Redis 状态键前缀，避免与其他业务键冲突。 */
    private static final String STATE_KEY_PREFIX = "aaf:agentscope:state:";

    /** Redis 只承担状态持久化；DELEGATED authority 由外层受控 Store 统一校验。 */
    @Bean
    @ConditionalOnMissingBean(RedisAgentStateStore.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    RedisAgentStateStore agentScopeRawStateStore(StringRedisTemplate redisTemplate) {
        return RedisAgentStateStore.builder()
                .clientAdapter(new SpringRedisClientAdapter(redisTemplate))
                .keyPrefix(STATE_KEY_PREFIX)
                .build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnBean(RedisAgentStateStore.class)
    DispatchGuardedAgentStateStore agentScopeAgentStateStore(
            RedisAgentStateStore rawStateStore, TaskUnitOfWork tasks) {
        return new DispatchGuardedAgentStateStore(rawStateStore, tasks);
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
    ToolResultEvidenceStore toolResultEvidenceStore() {
        return new ToolResultEvidenceStore();
    }

    @Bean
    AgentScopeEventMapper agentScopeEventMapper(ToolResultEvidenceStore evidenceStore) {
        return new AgentScopeEventMapper(evidenceStore);
    }

    @Bean
    AgentScopeTokenMeteringObserver tokenMeteringObserver(
            TokenMeteringPort metering, TaskUnitOfWork delegatedTasks) {
        return new AgentScopeTokenMeteringObserver(metering, delegatedTasks);
    }

    @Bean
    AgentScopeToolkitFactory agentScopeToolkitFactory(
            ToolCatalogPort toolCatalog,
            ToolGatewayPort toolGateway,
            ToolResultEvidenceStore evidenceStore,
            ObjectProvider<ContextAwareToolHandler> builtinHandlers) {
        return new AgentScopeToolkitFactory(
                toolCatalog, toolGateway, evidenceStore, builtinHandlers);
    }

    @Bean
    @ConditionalOnMissingBean(EffectiveToolResolver.class)
    EffectiveToolResolver effectiveToolResolver() {
        return new DefaultEffectiveToolResolver();
    }

    /** 每次物理模型调用在 provider 发送前冻结一份 PromptEnvelope。 */
    @Bean
    PromptEnvelopeCaptureMiddleware promptEnvelopeCaptureMiddleware(PromptEnvelopePort envelopes) {
        return new PromptEnvelopeCaptureMiddleware(envelopes, Clock.systemUTC());
    }

    /** 编译器持有 ReActAgent 缓存，销毁时须 close 释放。 */
    @Bean(destroyMethod = "close")
    AgentScopeSpecCompiler agentScopeSpecCompiler(
            DispatchGuardedAgentStateStore stateStore,
            AgentScopeToolkitFactory toolkitFactory,
            AgentScopeModelResolver modelResolver,
            PromptEnvelopeCaptureMiddleware envelopeCapture) {
        return new AgentScopeSpecCompiler(
                stateStore,
                toolkitFactory,
                modelResolver,
                envelopeCapture,
                AgentScopeSpecCompiler.DEFAULT_CACHE_CAPACITY);
    }

    /** L0 单例按 modelId 分桶缓存 ReActAgent，持有缓存，销毁时须 close 释放（依 ADR-007）。 */
    @Bean(destroyMethod = "close")
    L0ReActAgentFactory l0ReActAgentFactory(AgentScopeModelResolver modelResolver) {
        return new L0ReActAgentFactory(
                modelResolver, AgentScopeSpecCompiler.DEFAULT_CACHE_CAPACITY);
    }

    /** 非自主 L0 逻辑调用入口，替代旧 LlmClient（依 ADR-007）。 */
    @Bean
    PromptInvocationGateway promptInvocationGateway(
            L0ReActAgentFactory l0ReActAgentFactory, CapabilityRouter capabilityRouter) {
        return new PromptInvocationGateway(l0ReActAgentFactory, capabilityRouter);
    }

    @Bean
    @ConditionalOnMissingBean(AgentExecutionPort.class)
    AgentExecutionPort harnessAgentExecutionPort(
            AgentDefinitionPort definitions,
            AgentScopeSpecCompiler compiler,
            AgentScopeMessageMapper messageMapper,
            AgentScopeRuntimeContextMapper contextMapper,
            DispatchGuardedAgentStateStore stateStore,
            AgentScopeEventMapper eventMapper,
            AgentScopeTokenMeteringObserver meteringObserver,
            ExecutionEventStorePort eventStore,
            com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort leases,
            TaskUnitOfWork delegatedTasks,
            ToolResultEvidenceStore evidenceStore) {
        return new HarnessAgentExecutionAdapter(
                definitions,
                compiler,
                messageMapper,
                contextMapper,
                stateStore,
                eventMapper,
                meteringObserver,
                eventStore,
                leases,
                delegatedTasks,
                evidenceStore,
                // 源事件上的租约 / 任务 / 计量校验是同步阻塞 I/O，必须离开 AgentScope 事件发射线程
                Schedulers.boundedElastic());
    }
}
