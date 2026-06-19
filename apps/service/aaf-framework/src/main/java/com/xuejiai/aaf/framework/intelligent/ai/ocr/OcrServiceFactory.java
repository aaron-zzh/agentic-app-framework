package com.xuejiai.aaf.framework.intelligent.ai.ocr;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OCR 服务工厂，根据模型 providerType 路由到对应实现。
 *
 * <p>路由规则（按 providerType）：
 *
 * <ul>
 *   <li>DASHSCOPE → {@link DashScopeOcrService}
 *   <li>其他 → 抛出 {@link IllegalArgumentException}
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrServiceFactory {

    private final DashScopeOcrService dashScopeOcrService;

    /**
     * 根据 {@link AiModel} 路由到对应 OCR 服务实现。
     *
     * @param model 已由 CapabilityRouter 解析的模型
     * @return 对应 OCR 服务原始实现（不含积分装饰器）
     */
    public OcrService getService(AiModel model) {
        var providerType = model.effectiveProviderType();
        return switch (providerType) {
            case DASHSCOPE -> dashScopeOcrService;
            default ->
                    throw new IllegalArgumentException(
                            "模型 " + model.getModelId() + " 的协议类型 " + providerType + " 不支持 OCR");
        };
    }
}
