package com.xuejiai.aaf.framework.intelligent.ai.image;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 图像生成服务工厂，根据模型类型路由到对应实现。
 *
 * <p>路由规则（按 providerType）：
 *
 * <ul>
 *   <li>DASHSCOPE → {@link DashScopeImageGenerationService}（统一同步，内部三分支）
 *   <li>OPENAI_COMPAT → {@link SpringAiImageGenerationService}（images/generations，如 DALL-E /
 *       gpt-image-2）
 *   <li>其他 → 抛出 {@link IllegalArgumentException}
 * </ul>
 *
 * <p>所有 DASHSCOPE 模型均走同步路径（{@link #isAsyncModel} 返回 false）， 由 {@code AigcTaskExecutor.submitSync} 的
 * {@code @Async} 包装为非阻塞任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageServiceFactory {

    private final ChatBasedImageGenerationService chatBasedService;
    private final DashScopeImageGenerationService dashScopeService;
    private final DashScopeAsyncImageService dashScopeAsyncService;
    private final GeminiNativeImageGenerationService geminiNativeService;
    private final SpringAiImageGenerationService springAiService;
    private final AiModelRepository modelRepository;

    /** 获取异步图像生成服务（供旧路径 AiImageService 使用）。 */
    public AsyncImageGenerationService getAsyncService(String modelName) {
        return dashScopeAsyncService;
    }

    /** 根据 modelId 获取图像生成服务。 */
    public ImageGenerationService getSyncService(String modelId) {
        var model = modelRepository.findByModelIdAndEnabledTrue(modelId).orElse(null);
        log.info("[ImageServiceFactory] getSyncService: modelId={}", modelId);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在或已禁用: " + modelId);
        }
        var providerType = model.effectiveProviderType();
        return switch (providerType) {
            case DASHSCOPE -> dashScopeService;
            // case OPENAI_CHAT -> {
            //     log.info("[ImageServiceFactory] 路由到 ChatBasedImageGenerationService: modelId={}",
            // modelId);
            //     yield chatBasedService;
            // }
            case OPENAI_COMPAT -> {
                // Gemini image 模型需走原生 generateContent 接口，不能用 OpenAI images/generations
                if (model.getModelName() != null
                        && model.getModelName().startsWith("gemini-")
                        && model.hasCapability("IMAGE_GEN")) {
                    log.info(
                            "[ImageServiceFactory] 路由到 GeminiNativeImageGenerationService: modelId={}",
                            modelId);
                    yield geminiNativeService;
                }
                yield springAiService;
            }
            default ->
                    throw new IllegalArgumentException(
                            "模型 " + modelId + " 的协议类型 " + providerType + " 不支持图像生成");
        };
    }

    /**
     * 判断是否为异步图像生成模型。
     *
     * <p>当前所有支持的图像模型均走同步路径，统一返回 false。
     */
    public boolean isAsyncModel(AiModel model) {
        return false;
    }
}
