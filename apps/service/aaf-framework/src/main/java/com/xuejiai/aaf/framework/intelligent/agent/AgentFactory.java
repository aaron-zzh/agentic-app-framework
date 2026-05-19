/**
 * Agent 工厂。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.agent;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.core.token.TokenMeteringHook;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.OpenAIChatModel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 根据 AgentDefinition 构建 AgentScope ReActAgent 实例。
 * 集成 MCP 工具发现、认知循环（ReAct）、超时控制。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentFactory {

    private final TokenMeteringHook tokenMeteringHook;
    private final McpToolService mcpToolService;

    /**
     * 创建 Agent 实例。
     * 每次请求创建独立实例（AgentScope Agent 是有状态的，不可并发共享）。
     */
    public ReActAgent create(AgentDefinition definition) {
        var builder = ReActAgent.builder()
                .name(definition.getName())
                .sysPrompt(definition.getSystemPrompt())
                .hook(tokenMeteringHook);

        // 配置模型
        configureModel(builder, definition);

        // 配置工具（MCP + 本地）
        builder.toolkit(mcpToolService.buildToolkit(definition));

        return builder.build();
    }

    private void configureModel(ReActAgent.Builder builder, AgentDefinition definition) {
        var model = OpenAIChatModel.builder()
                .modelName(definition.getModelId() != null ? definition.getModelId() : "gpt-4o")
                .build();
        builder.model(model);
    }
}
