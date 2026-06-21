package com.xuejiai.aaf.framework.intelligent.ai.speech;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;

import lombok.RequiredArgsConstructor;

/**
 * 语音服务工厂，根据模型 providerType 路由到对应实现。
 *
 * <p>当前支持：DASHSCOPE → {@link DashScopeSpeechService}
 */
@Component
@RequiredArgsConstructor
public class SpeechServiceFactory {

    private final DashScopeSpeechService dashScopeSpeechService;

    /**
     * 根据 {@link AiModel} 路由到对应语音服务实现。
     *
     * @return 原始实现（不含积分装饰器）
     */
    public SpeechService getService(AiModel model) {
        var providerType = model.effectiveProviderType();
        return switch (providerType) {
            case DASHSCOPE -> dashScopeSpeechService;
            default ->
                    throw new IllegalArgumentException(
                            "模型 " + model.getModelId() + " 的协议类型 " + providerType + " 不支持语音合成");
        };
    }
}
