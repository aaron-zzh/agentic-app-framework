/**
 * 弹性对话服务（降级 + 计量）。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.ai;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 包装 ChatClient 调用，提供主模型异常时切换 fallback 模型的降级能力，
 * 并在每次调用后发布 {@link TokenUsageEvent}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientChatService {

    private final ChatClient chatClient;
    private final AiProperties properties;
    private final ModelRouter modelRouter;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 同步调用 LLM，主模型失败时降级到 fallback。
     *
     * @param messages 消息列表
     * @param scene 场景名（用于路由模型）
     * @param userId 当前用户 ID（用于计量）
     * @return ChatResponse
     */
    public ChatResponse call(List<Message> messages, String scene, Long userId) {
        var config = modelRouter.resolve(scene);
        try {
            var response = doCall(messages, config);
            publishUsage(response, userId, config.getModel());
            return response;
        } catch (Exception e) {
            log.warn("主模型 [{}] 调用失败，尝试降级: {}", config.getModel(), e.getMessage());
            return callFallback(messages, userId);
        }
    }

    /**
     * 流式调用 LLM，主模型失败时降级到 fallback。
     *
     * @param messages 消息列表
     * @param scene 场景名
     * @param userId 当前用户 ID
     * @return 流式响应
     */
    public Flux<ChatResponse> stream(List<Message> messages, String scene, Long userId) {
        var config = modelRouter.resolve(scene);
        try {
            return doStream(messages, config);
        } catch (Exception e) {
            log.warn("主模型 [{}] 流式调用失败，尝试降级: {}", config.getModel(), e.getMessage());
            var fallbackConfig = resolveFallback();
            return doStream(messages, fallbackConfig);
        }
    }

    private ChatResponse callFallback(List<Message> messages, Long userId) {
        var fallbackConfig = resolveFallback();
        var response = doCall(messages, fallbackConfig);
        publishUsage(response, userId, fallbackConfig.getModel());
        return response;
    }

    private ChatResponse doCall(List<Message> messages, AiProperties.ModelConfig config) {
        var options = buildOptions(config);
        var prompt = new Prompt(messages, options);
        return chatClient.prompt(prompt).call().chatResponse();
    }

    private Flux<ChatResponse> doStream(List<Message> messages, AiProperties.ModelConfig config) {
        var options = buildOptions(config);
        var prompt = new Prompt(messages, options);
        return chatClient.prompt(prompt).stream().chatResponse();
    }

    private OpenAiChatOptions buildOptions(AiProperties.ModelConfig config) {
        var builder = OpenAiChatOptions.builder();
        if (config.getModel() != null) {
            builder.model(config.getModel());
        }
        if (config.getMaxTokens() != null) {
            builder.maxTokens(config.getMaxTokens());
        }
        if (config.getTemperature() != null) {
            builder.temperature(config.getTemperature());
        }
        return builder.build();
    }

    private AiProperties.ModelConfig resolveFallback() {
        var fallbackKey = properties.getFallbackModel();
        if (fallbackKey != null && properties.getModels().containsKey(fallbackKey)) {
            return properties.getModels().get(fallbackKey);
        }
        return modelRouter.resolve(properties.getDefaultModel());
    }

    private void publishUsage(ChatResponse response, Long userId, String model) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return;
        }
        var usage = response.getMetadata().getUsage();
        eventPublisher.publishEvent(new TokenUsageEvent(
                userId,
                model,
                usage.getPromptTokens(),
                usage.getCompletionTokens()));
    }
}
