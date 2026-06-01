package com.xuejiai.aaf.module.ai.chat.agui;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.intelligent.agent.agentscope.ChatSessionResolver;

import io.agentscope.core.agui.adapter.AguiAdapterConfig;
import io.agentscope.core.agui.processor.AguiRequestProcessor;
import io.agentscope.core.agui.registry.AguiAgentRegistry;
import io.agentscope.spring.boot.agui.common.AguiProperties;
import io.agentscope.spring.boot.agui.common.ThreadSessionManager;
import io.agentscope.spring.boot.agui.mvc.AguiMvcController;
import io.agentscope.spring.boot.agui.mvc.AguiRestController;

/**
 * AG-UI 链路配置——用 {@link AafAgentResolver} 替换默认 resolver，
 * 覆盖 starter 的 {@link AguiRestController}，使 /agui/runs 链路具备
 * 上下文设置 + 历史播种 + 记忆检索注入能力。
 */
@Configuration
public class AafAguiConfiguration {

    @Bean
    public AguiRequestProcessor aafAguiRequestProcessor(
            AguiAgentRegistry registry,
            ThreadSessionManager sessionManager,
            ChatSessionResolver chatSessionResolver,
            AguiProperties props) {
        var resolver = new AafAgentResolver(registry, sessionManager, chatSessionResolver);
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
            AguiRequestProcessor aafAguiRequestProcessor) {
        return new AafAguiRestController(aguiMvcController, props, aafAguiRequestProcessor);
    }
}
