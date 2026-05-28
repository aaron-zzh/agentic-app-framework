/**
 * 动态 ChatClient 工厂。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.ai.chat;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 按 modelId 从数据库读取配置，动态构建 Spring AI {@link ChatClient}。
 *
 * <p>OPENAI_COMPAT 直接构建；ANTHROPIC / OLLAMA 从 Spring 容器取已配置的 Bean（需引入对应 starter）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicChatClientFactory {

    private final AiModelRepository modelRepository;
    private final ApplicationContext applicationContext;

    private final Cache<String, ChatClient> cache =
            Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(200).build();

    public ChatClient get(String modelId) {
        return cache.get(modelId, this::build);
    }

    public void evict(String modelId) {
        cache.invalidate(modelId);
        log.debug("ChatClient 缓存已失效: modelId={}", modelId);
    }

    private ChatClient build(String modelId) {
        var model =
                modelRepository
                        .findByModelIdAndEnabledTrue(modelId)
                        .orElseThrow(() -> exception(GlobalErrorCode.NOT_FOUND));
        log.debug(
                "构建 ChatClient: modelId={}, provider={}, type={}",
                modelId,
                model.getProvider(),
                model.getProviderType());
        var chatModel =
                switch (model.getProviderType()) {
                    case AiModel.PROVIDER_TYPE_OPENAI_COMPAT -> buildOpenAiCompat(model);
                    case AiModel.PROVIDER_TYPE_ANTHROPIC, AiModel.PROVIDER_TYPE_OLLAMA ->
                            buildFromContainer(model);
                    default -> throw exception(GlobalErrorCode.BAD_REQUEST);
                };
        return ChatClient.builder(chatModel).build();
    }

    private OpenAiChatModel buildOpenAiCompat(AiModel model) {
        // Spring AI 2.0.0-M6 使用官方 OpenAI Java SDK，通过 OpenAiSetup 构建 client
        var syncClient =
                OpenAiSetup.setupSyncClient(
                        model.getBaseUrl(),
                        model.getApiKey() != null ? model.getApiKey() : "",
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        model.getModelName(),
                        Duration.ofSeconds(60),
                        2,
                        null,
                        null);
        var asyncClient =
                OpenAiSetup.setupAsyncClient(
                        model.getBaseUrl(),
                        model.getApiKey() != null ? model.getApiKey() : "",
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        model.getModelName(),
                        Duration.ofSeconds(60),
                        2,
                        null,
                        null);
        var options =
                OpenAiChatOptions.builder()
                        .model(model.getModelName())
                        .temperature(model.getTemperature())
                        .maxTokens(model.getMaxTokens())
                        .build();
        return OpenAiChatModel.builder()
                .openAiClient(syncClient)
                .openAiClientAsync(asyncClient)
                .options(options)
                .build();
    }

    /**
     * ANTHROPIC / OLLAMA：从 Spring 容器取已自动配置的 ChatModel Bean。 需在 aaf-api 引入对应 starter 并配置
     * application.yaml。
     */
    @SuppressWarnings("unchecked")
    private ChatModel buildFromContainer(AiModel model) {
        var className =
                switch (model.getProviderType()) {
                    case AiModel.PROVIDER_TYPE_ANTHROPIC ->
                            "org.springframework.ai.anthropic.AnthropicChatModel";
                    case AiModel.PROVIDER_TYPE_OLLAMA ->
                            "org.springframework.ai.ollama.OllamaChatModel";
                    default -> throw exception(GlobalErrorCode.BAD_REQUEST);
                };
        try {
            var clazz = (Class<ChatModel>) Class.forName(className);
            return applicationContext.getBean(clazz);
        } catch (ClassNotFoundException | NoSuchBeanDefinitionException e) {
            log.error("模型 [{}] 需要 {} 但对应 starter 未引入或未配置", model.getModelId(), className);
            throw exception(GlobalErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
