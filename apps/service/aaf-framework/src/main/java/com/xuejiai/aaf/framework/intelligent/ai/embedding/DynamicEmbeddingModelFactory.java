package com.xuejiai.aaf.framework.intelligent.ai.embedding;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import com.xuejiai.aaf.framework.intelligent.ai.chat.AiProperties;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelProviderType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 按能力路由选出的 {@link AiModel} 动态构建 Spring AI {@link EmbeddingModel}，与 {@code DynamicChatClientFactory}
 * 的凭证解析顺序保持一致。
 *
 * <p>仅支持 OPENAI_COMPAT 协议（含 DashScope compatible-mode）；缓存键包含连接参数，模型配置变更后自然换新实例。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicEmbeddingModelFactory {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final AiProperties aiProperties;

    private final Cache<String, EmbeddingModel> cache =
            Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(50).build();

    /**
     * 获取模型对应的 EmbeddingModel。
     *
     * @param dimensions 输出向量维度，需与向量库列维度一致
     */
    public EmbeddingModel get(AiModel model, int dimensions) {
        Objects.requireNonNull(model, "model 不能为空");
        if (model.effectiveProviderType() != AiModelProviderType.OPENAI_COMPAT) {
            throw new IllegalStateException(
                    "EMBEDDING 仅支持 OPENAI_COMPAT 协议: modelId=%s, type=%s"
                            .formatted(model.getModelId(), model.effectiveProviderType()));
        }
        var connection = resolveConnection(model);
        var key =
                "%s|%s|%s|%s|%d"
                        .formatted(
                                model.getModelId(),
                                model.getModelName(),
                                connection.baseUrl(),
                                Integer.toHexString(Objects.hashCode(connection.apiKey())),
                                dimensions);
        return cache.get(key, ignored -> build(model, connection, dimensions));
    }

    private EmbeddingModel build(AiModel model, Connection connection, int dimensions) {
        // 重试由 EmbeddingService 统一控制，SDK 层不再叠加重试
        var client =
                OpenAiSetup.setupSyncClient(
                        connection.baseUrl(),
                        connection.apiKey(),
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        model.getModelName(),
                        TIMEOUT,
                        0,
                        null,
                        null);
        var options = OpenAiEmbeddingOptions.builder();
        options.model(model.getModelName());
        options.dimensions(dimensions);
        log.info(
                "[EmbeddingModel] modelId={}, modelName={}, baseUrl={}, dimensions={}, apiKey=****{}",
                model.getModelId(),
                model.getModelName(),
                connection.baseUrl(),
                dimensions,
                connection.apiKey().length() > 4
                        ? connection.apiKey().substring(connection.apiKey().length() - 4)
                        : "?");
        return new OpenAiEmbeddingModel(client, MetadataMode.EMBED, options.build());
    }

    /** apiKey/baseUrl 优先级：模型级 > 供应商级 > yaml aaf.ai.models.{provider} > default。 */
    private Connection resolveConnection(AiModel model) {
        var apiKey = model.effectiveApiKey();
        var baseUrl = model.effectiveBaseUrl();
        if (isBlank(apiKey) || isBlank(baseUrl)) {
            var models = aiProperties.getModels();
            var fallback = models.getOrDefault(model.getProvider(), models.get("default"));
            if (fallback != null) {
                if (isBlank(apiKey)) {
                    apiKey = fallback.getApiKey();
                }
                if (isBlank(baseUrl)) {
                    baseUrl = fallback.getBaseUrl();
                }
            }
        }
        if (isBlank(baseUrl)) {
            throw new IllegalStateException(
                    "EMBEDDING 模型缺少 baseUrl: modelId=" + model.getModelId());
        }
        return new Connection(baseUrl, apiKey == null ? "" : apiKey);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Connection(String baseUrl, String apiKey) {}
}
