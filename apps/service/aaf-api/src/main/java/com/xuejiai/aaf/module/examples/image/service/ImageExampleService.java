package com.xuejiai.aaf.module.examples.image.service;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.ai.image.ImageProcessService;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageServiceFactory;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageResult;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * 图像能力示例服务。
 *
 * <p>只调用封装好的接口，不直接使用 SDK：
 *
 * <ul>
 *   <li>{@link ImageServiceFactory} — 文生图路由
 *   <li>{@link ImageProcessService} — 图像处理
 * </ul>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aaf.examples.image.enabled", havingValue = "true")
public class ImageExampleService {

    private final ImageServiceFactory imageServiceFactory;
    private final ImageProcessService imageProcessService;
    private final CapabilityRouter capabilityRouter;

    /** 文生图示例 */
    public ImageResult generate(GenerateRequest req) {
        var model =
                capabilityRouter.resolve(
                        CapabilityRoutingContext.of(
                                null, CapabilityRoutingContext.CAP_IMAGE_GEN, req.modelId()));
        return imageServiceFactory
                .getSyncService(model)
                .generate(
                        model,
                        new ImageRequest(
                                req.prompt(), model.getModelId(), req.width(), req.height(), "url"));
    }

    /** 图像处理示例 */
    public ImageProcessService.ProcessResult process(ProcessRequest req) {
        return imageProcessService.process(
                new ImageProcessService.ProcessRequest(
                        req.imageUrl(), req.method(), req.options()));
    }

    /** 查询异步任务（卡通化等） */
    public ImageProcessService.ProcessResult queryTask(String taskId) {
        return imageProcessService.queryTask(taskId);
    }

    public record GenerateRequest(
            @NotBlank String prompt, String modelId, Integer width, Integer height) {}

    public record ProcessRequest(
            @NotBlank String imageUrl, @NotBlank String method, Map<String, String> options) {}
}
