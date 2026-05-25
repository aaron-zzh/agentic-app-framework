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
    private final com.xuejiai.aaf.framework.intelligent.core.model.ModelManagementService modelManagementService;

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
        } else if (modelId != null) {
            // 尝试 fallback 模型
            var fallback = modelManagementService.getFallback(modelId).orElse(null);
            if (fallback != null) {
                log.info("模型 [{}] 不可用，降级到 fallback [{}]", modelId, fallback.getModelId());
                chatModel = buildFromDb(fallback);
            } else {
                log.warn("模型 [{}] 不可用且无 fallback，降级使用模型名直接调用", modelId);
                chatModel = OpenAIChatModel.builder().modelName(modelId).build();
            }
        } else {
            chatModel = OpenAIChatModel.builder().modelName("gpt-4o").build();
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
