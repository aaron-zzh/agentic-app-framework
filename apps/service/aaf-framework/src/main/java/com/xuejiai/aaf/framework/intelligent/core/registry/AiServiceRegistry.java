package com.xuejiai.aaf.framework.intelligent.core.registry;

import com.xuejiai.aaf.framework.intelligent.core.AiCapability;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;

/**
 * 统一 AI 服务工厂接口。
 *
 * <p>调用方通过 {@link com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter} 解析 {@link
 * AiModel} 后，用此工厂获取对应能力实现（含积分装饰器）：
 *
 * <pre>{@code
 * var model = capabilityRouter.resolve(ctx);
 * var svc = aiServiceRegistry.get(ImageGenerationService.class, model);
 * svc.generate(model, request);
 * }</pre>
 *
 * <p>实现类通过 {@link AiServiceProvider} SPI 自动注册，无需手动维护工厂路由逻辑。
 */
public interface AiServiceRegistry {

    /**
     * 根据能力类型和模型获取对应实现（含积分装饰器）。
     *
     * @param type 能力接口 Class
     * @param model 已由 CapabilityRouter 解析的模型
     * @param <T> 能力接口类型
     * @return 对应实现，永不为 null
     * @throws IllegalArgumentException 找不到支持该模型的实现时
     */
    <T extends AiCapability> T get(Class<T> type, AiModel model);
}
