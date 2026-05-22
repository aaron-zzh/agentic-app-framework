package com.xuejiai.aaf.framework.intelligent.ai.image;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 图像生成服务工厂，根据 modelId 前缀路由到对应实现。
 *
 * <p>路由规则（modelId 前缀）：
 * <ul>
 *   <li>{@code qwen-image*} / {@code wanx*} → {@link WanxImageGenerationService}（异步）
 *   <li>{@code dall-e*} / 其他 → {@link SpringAiImageGenerationService}（同步）
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageServiceFactory {

    private final List<AsyncImageGenerationService> asyncServices;
    private final List<ImageGenerationService> syncServices;

    /**
     * 根据 modelId 获取异步图像生成服务（wanx 系列）。
     *
     * @throws IllegalStateException 无可用实现时
     */
    public AsyncImageGenerationService getAsyncService(String modelId) {
        if (asyncServices.isEmpty()) {
            throw new IllegalStateException("无可用的异步图像生成服务，请检查 DASHSCOPE_API_KEY 配置");
        }
        // 当前只有 WanxImageGenerationService，直接返回第一个
        return asyncServices.get(0);
    }

    /**
     * 根据 modelId 获取同步图像生成服务（DALL-E 等）。
     *
     * @throws IllegalStateException 无可用实现时
     */
    public ImageGenerationService getSyncService(String modelId) {
        if (syncServices.isEmpty()) {
            throw new IllegalStateException("无可用的同步图像生成服务，请检查 Spring AI 配置");
        }
        return syncServices.get(0);
    }

    /** 判断 modelId 是否为异步模型（wanx / qwen-image 系列） */
    public boolean isAsyncModel(String modelId) {
        if (modelId == null) return true; // 默认走异步（wanx）
        String lower = modelId.toLowerCase();
        return lower.startsWith("wanx") || lower.startsWith("qwen-image");
    }
}
