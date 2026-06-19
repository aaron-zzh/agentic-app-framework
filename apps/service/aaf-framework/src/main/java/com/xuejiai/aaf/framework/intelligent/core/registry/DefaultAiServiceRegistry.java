package com.xuejiai.aaf.framework.intelligent.core.registry;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageServiceFactory;
import com.xuejiai.aaf.framework.intelligent.ai.image.decorator.ImageGenServiceDecorator;
import com.xuejiai.aaf.framework.intelligent.ai.music.MusicGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.music.MusicServiceFactory;
import com.xuejiai.aaf.framework.intelligent.ai.music.decorator.MusicServiceDecorator;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.OcrService;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.OcrServiceFactory;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.decorator.OcrServiceDecorator;
import com.xuejiai.aaf.framework.intelligent.ai.video.VideoGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.video.VideoServiceFactory;
import com.xuejiai.aaf.framework.intelligent.core.AiCapability;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 统一 AI 服务注册表——两级路由实现。
 *
 * <p>第一级（本类）：按能力类型（{@link Class}）委托到对应工厂。 第二级（各工厂）：按 providerType / modelName 路由到具体实现。
 *
 * <p>同步能力在此处包装服务装饰器后返回，调用方无需感知积分逻辑。 Video 为异步任务，积分在任务回调中手动结算，直接返回原始实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAiServiceRegistry implements AiServiceRegistry {

    private final ImageServiceFactory imageServiceFactory;
    private final VideoServiceFactory videoServiceFactory;
    private final OcrServiceFactory ocrServiceFactory;
    private final MusicServiceFactory musicServiceFactory;
    private final AiCreditGuard creditGuard;
    private final OperatorContext operatorContext;

    @Override
    @SuppressWarnings("unchecked")
    public <T extends AiCapability> T get(Class<T> type, AiModel model) {
        log.debug(
                "[AiServiceRegistry] get: capability={}, modelId={}",
                type.getSimpleName(),
                model.getModelId());

        if (type == ImageGenerationService.class) {
            var raw = imageServiceFactory.getSyncService(model);
            return (T) new ImageGenServiceDecorator(raw, creditGuard, operatorContext);
        }
        if (type == VideoGenerationService.class) {
            return (T) videoServiceFactory.getService(model);
        }
        if (type == OcrService.class) {
            var raw = ocrServiceFactory.getService(model);
            return (T) new OcrServiceDecorator(raw, creditGuard, operatorContext);
        }
        if (type == MusicGenerationService.class) {
            var raw = musicServiceFactory.getService(model);
            return (T) new MusicServiceDecorator(raw, creditGuard, operatorContext);
        }

        throw new IllegalArgumentException("AiServiceRegistry 未注册能力类型: " + type.getSimpleName());
    }
}
