package com.xuejiai.aaf.module.ai.agui;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.intelligent.core.assistant.ChatSessionResolver;

import io.agentscope.core.agui.adapter.AguiAdapterConfig;
import io.agentscope.core.agui.processor.AguiRequestProcessor;
import io.agentscope.core.agui.registry.AguiAgentRegistry;
import io.agentscope.core.session.Session;
import io.agentscope.core.session.redis.RedisSession;
import io.agentscope.spring.boot.agui.common.AguiProperties;
import io.agentscope.spring.boot.agui.common.ThreadSessionManager;
import io.agentscope.spring.boot.agui.mvc.AguiMvcController;
import io.agentscope.spring.boot.agui.mvc.AguiRestController;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;

/**
 * AG-UI 链路配置——用 {@link AafAgentResolver} 替换默认 resolver，
 * 覆盖 starter 的 {@link AguiRestController}，使 /agui/runs 链路具备
 * 上下文设置 + 历史播种 + 记忆检索注入 + Agent 状态持久化能力。
 */
@Configuration
public class AafAguiConfiguration {

    @Bean
    public Session agentScopeSession(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port) {
        var redisClient = RedisClient.create(RedisURI.builder().withHost(host).withPort(port).build());
        return RedisSession.builder().lettuceClient(redisClient).keyPrefix("aaf:agent:session:").build();
    }

    @Bean
    public AguiRequestProcessor aafAguiRequestProcessor(
            AguiAgentRegistry registry,
            ThreadSessionManager sessionManager,
            ChatSessionResolver chatSessionResolver,
            Session agentScopeSession,
            AguiProperties props) {
        var resolver = new AafAgentResolver(registry, sessionManager, chatSessionResolver, agentScopeSession);
        var config = AguiAdapterConfig.builder()
                .emitStateEvents(props.isEmitStateEvents())
                .emitToolCallArgs(props.isEmitToolCallArgs())
                .enableReasoning(props.isEnableReasoning())
                .defaultAgentId(props.getDefaultAgentId())
                .build();
        return AguiRequestProcessor.builder()
                .agentResolver(resolver)
                .config(config)
                .build();
    }

    @Bean
    public AguiRestController aguiRestController(
            AguiMvcController aguiMvcController,
            AguiProperties props,
            AguiRequestProcessor aafAguiRequestProcessor,
            Session agentScopeSession) {
        return new AafAguiRestController(aguiMvcController, props, aafAguiRequestProcessor, agentScopeSession);
    }
}
