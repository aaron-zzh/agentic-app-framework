/**
 * Agent 工厂。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.agent;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.core.agent.AgentExecutor;
import com.xuejiai.aaf.framework.intelligent.core.token.TokenMeteringHook;
import com.xuejiai.aaf.framework.intelligent.agent.agentscope.AgentScopeAgentAdapter;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.OpenAIChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 根据 AgentDefinition 构建 AgentExecutor 实例。
 *
 * <p>模型配置优先从数据库（{@link AiModelRepository}）读取，找不到时降级到 AgentDefinition.modelId
 * 直接作为模型名（兼容旧行为）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentFactory {

    private final TokenMeteringHook tokenMeteringHook;
    private final McpToolService mcpToolService;
    private final AiModelRepository modelRepository;

    /** 创建 AgentExecutor 实例。每次请求创建独立实例（AgentScope Agent 是有状态的，不可并发共享）。 */
    public AgentExecutor create(AgentDefinition definition) {
        var builder =
                ReActAgent.builder()
                        .name(definition.getName())
                        .sysPrompt(definition.getSystemPrompt())
                        .hook(tokenMeteringHook);

        configureModel(builder, definition);
        builder.toolkit(mcpToolService.buildToolkit(definition));

        return new AgentScopeAgentAdapter(builder.build());
    }

    private void configureModel(ReActAgent.Builder builder, AgentDefinition definition) {
        var modelId = definition.getModelId();
        // 优先从数据库读取完整配置
        var dbModel = modelId != null ? modelRepository.findByModelIdAndEnabledTrue(modelId).orElse(null) : null;

        OpenAIChatModel chatModel;
        if (dbModel != null) {
            chatModel = buildFromDb(dbModel);
        } else {
            // 降级：直接用 modelId 作为模型名（依赖环境变量中的 apiKey）
            log.warn("数据库中未找到启用的模型 [{}]，降级使用模型名直接调用", modelId);
            chatModel = OpenAIChatModel.builder()
                    .modelName(modelId != null ? modelId : "gpt-4o")
                    .build();
        }
        builder.model(chatModel);
    }

    private OpenAIChatModel buildFromDb(AiModel model) {
        return OpenAIChatModel.builder()
                .modelName(model.getModelName())
                .apiKey(model.getApiKey() != null ? model.getApiKey() : "")
                .baseUrl(model.getBaseUrl())
                .build();
    }
}
