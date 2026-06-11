package com.xuejiai.aaf.framework.intelligent.ai.image;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.ai.image.ImageModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.intelligent.ai.chat.AiProperties;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelProviderType;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 按 modelId 动态构建 OpenAI 兼容的 {@link ImageModel}，支持多供应商（OpenAI / N1N 等）。
 *
 * <p>缓存策略与 {@link com.xuejiai.aaf.framework.intelligent.ai.chat.DynamicChatClientFactory} 对齐： 按
 * modelId 缓存 10 分钟，最多 200 个。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicImageModelFactory {

    private final AiModelRepository modelRepository;
    private final AiProperties aiProperties;

    private final Cache<String, ImageModel> cache =
            Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(200).build();

    public ImageModel get(String modelId) {
        return cache.get(modelId, this::build);
    }

    public void evict(String modelId) {
        cache.invalidate(modelId);
        log.debug("ImageModel 缓存已失效: modelId={}", modelId);
    }

    private ImageModel build(String modelId) {
        var model =
                modelRepository
                        .findByModelIdAndEnabledTrue(modelId)
                        .orElseThrow(() -> exception(GlobalErrorCode.NOT_FOUND));

        if (model.effectiveProviderType() != AiModelProviderType.OPENAI_COMPAT) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }

        // apiKey 优先级：模型级 > 供应商级 > yaml aaf.ai.models.{provider}.api-key >
        // aaf.ai.models.default.api-key
        String apiKey = model.effectiveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            var models = aiProperties.getModels();
            var cfg = models.getOrDefault(model.getProvider(), models.get("default"));
            if (cfg != null) {
                apiKey = cfg.getApiKey();
            }
        }

        log.debug("构建 ImageModel: modelId={}, baseUrl={}", modelId, model.effectiveBaseUrl());
        log.info(
                "[DynamicImageModelFactory] 图片生成模型: modelId={}, modelName={}",
                modelId,
                model.getModelName());

        var openAiClient =
                OpenAiSetup.setupSyncClient(
                        model.effectiveBaseUrl(),
                        apiKey != null ? apiKey : "",
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

        var defaultOptions = OpenAiImageOptions.builder().model(model.getModelName()).build();

        return new OpenAiImageModel(openAiClient, defaultOptions);
    }
}
