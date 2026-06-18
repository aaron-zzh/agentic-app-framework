package com.xuejiai.aaf.framework.intelligent.ai.image;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelManagementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 图像生成服务工厂，根据模型 providerType 路由到对应实现。
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
 * <p>推荐调用方通过 {@link com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter}
 * 获取 {@link AiModel}，再调用 {@link #getSyncService(AiModel)} 取实现，最后调用接口方法。
 * 这与 OcrController 的模式一致，确保用户偏好 / 系统默认等决策链正确生效。
 *
 * <p>所有 DASHSCOPE 模型均走同步路径（{@link #isAsyncModel} 返回 false）， 由 {@code AigcTaskExecutor.submitSync}
 * 的 {@code @Async} 包装为非阻塞任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageServiceFactory {

    private final DashScopeImageGenerationService dashScopeService;
    private final DashScopeAsyncImageService dashScopeAsyncService;
    private final GeminiNativeImageGenerationService geminiNativeService;
    private final SpringAiImageGenerationService springAiService;
    private final ModelManagementService modelManagementService;

    /** 获取异步图像生成服务（供旧路径 AiImageService 使用）。 */
    public AsyncImageGenerationService getAsyncService(String modelName) {
        return dashScopeAsyncService;
    }

    /**
     * 根据 {@link AiModel} 路由到对应图像生成服务实现（推荐，model 由 CapabilityRouter 解析）。
     *
     * @param model 已由 CapabilityRouter 解析的模型
     * @return 对应图像生成服务实现
     */
    public ImageGenerationService getSyncService(AiModel model) {
        var providerType = model.effectiveProviderType();
        return switch (providerType) {
            case DASHSCOPE -> dashScopeService;
            case OPENAI_COMPAT -> {
                // Gemini image 模型需走原生 generateContent 接口，不能用 OpenAI images/generations
                if (model.getModelName() != null
                        && model.getModelName().startsWith("gemini-")
                        && model.hasCapability("IMAGE_GEN")) {
                    log.info(
                            "[ImageServiceFactory] 路由到 GeminiNativeImageGenerationService: modelId={}",
                            model.getModelId());
                    yield geminiNativeService;
                }
                yield springAiService;
            }
            default ->
                    throw new IllegalArgumentException(
                            "模型 "
                                    + model.getModelId()
                                    + " 的协议类型 "
                                    + providerType
                                    + " 不支持图像生成");
        };
    }

    /**
     * 根据 modelId 路由到对应图像生成服务实现（走缓存查模型）。
     *
     * <p>注意：此方法跳过用户偏好/系统默认决策链，仅适用于已明确 modelId 的场景（如 AigcTaskExecutor）。
     * 其他场景请先通过 CapabilityRouter 解析 AiModel，再调用 {@link #getSyncService(AiModel)}。
     *
     * @param modelId ai_model 表中的 modelId
     * @return 对应图像生成服务实现
     */
    public ImageGenerationService getSyncService(String modelId) {
        var model = modelManagementService.getModel(modelId);
        log.info("[ImageServiceFactory] getSyncService: modelId={}", modelId);
        return getSyncService(model);
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
