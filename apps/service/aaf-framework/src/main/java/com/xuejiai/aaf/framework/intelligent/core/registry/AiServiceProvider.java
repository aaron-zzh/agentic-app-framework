package com.xuejiai.aaf.framework.intelligent.core.registry;

import com.xuejiai.aaf.framework.intelligent.core.AiCapability;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;

/**
 * AI 服务实现注册 SPI。
 *
 * <p>实现类同时实现此接口和对应的能力接口（如 ImageGenerationService）， Spring 启动时 {@link DefaultAiServiceRegistry}
 * 自动收集所有 AiServiceProvider bean 完成注册。
 *
 * <p>示例：
 *
 * <pre>{@code
 * @Service
 * public class DashScopeImageGenerationService
 *         implements ImageGenerationService, AiServiceProvider<ImageGenerationService> {
 *
 *     public Class<ImageGenerationService> capabilityType() { return ImageGenerationService.class; }
 *     public boolean supports(AiModel model) {
 *         return model.effectiveProviderType() == AiModelProviderType.DASHSCOPE;
 *     }
 * }
 * }</pre>
 */
public interface AiServiceProvider<T extends AiCapability> {

    /** 声明支持的能力接口类型。 */
    Class<T> capabilityType();

    /**
     * 路由条件：根据 AiModel 判断此实现是否能处理该模型。
     *
     * <p>可检查 providerType、modelName 前缀等任意条件。
     */
    boolean supports(AiModel model);
}
