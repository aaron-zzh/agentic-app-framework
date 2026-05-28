package com.xuejiai.aaf.framework.intelligent.ai.image;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 DashScope SDK 的通义万象文生图实现（异步任务模式）。
 *
 * <p>支持模型：qwen-image-plus、qwen-image、wanx-v1 等。
 *
 * <p>启用条件：配置 {@code spring.ai.dashscope.api-key}
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.ai.dashscope.api-key", matchIfMissing = false)
public class WanxImageGenerationService implements AsyncImageGenerationService {

    private static final String DEFAULT_MODEL = "qwen-image-plus";

    private final String apiKey;
    private final ImageSynthesis imageSynthesis = new ImageSynthesis();

    public WanxImageGenerationService(@Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String submitTask(AsyncImageRequest request) {
        try {
            String model =
                    (request.modelId() != null && !request.modelId().isBlank())
                            ? request.modelId()
                            : DEFAULT_MODEL;
            String size = request.width() + "*" + request.height();

            var param =
                    ImageSynthesisParam.builder()
                            .apiKey(apiKey)
                            .model(model)
                            .prompt(request.prompt())
                            .n(1)
                            .size(size)
                            .build();

            var result = imageSynthesis.asyncCall(param);
            String taskId = result.getOutput().getTaskId();
            log.info("[WanxImage] 任务提交成功: model={}, taskId={}", model, taskId);
            return taskId;
        } catch (ApiException | NoApiKeyException e) {
            log.error("[WanxImage] 任务提交失败: prompt={}", request.prompt(), e);
            throw new RuntimeException("文生图任务提交失败: " + e.getMessage(), e);
        }
    }

    @Override
    public AsyncImageResult queryTask(String taskId) {
        try {
            var result = imageSynthesis.fetch(taskId, apiKey);
            String status = result.getOutput().getTaskStatus();
            return switch (status) {
                case "SUCCEEDED" -> {
                    var results = result.getOutput().getResults();
                    String url =
                            (results != null && !results.isEmpty())
                                    ? results.get(0).get("url")
                                    : null;
                    log.info("[WanxImage] 任务完成: taskId={}, url={}", taskId, url);
                    yield AsyncImageResult.succeeded(taskId, url);
                }
                case "FAILED" -> {
                    String msg = result.getOutput().getMessage();
                    log.warn("[WanxImage] 任务失败: taskId={}, msg={}", taskId, msg);
                    yield AsyncImageResult.failed(taskId, msg);
                }
                default -> AsyncImageResult.pending(taskId);
            };
        } catch (ApiException | NoApiKeyException e) {
            log.error("[WanxImage] 查询任务失败: taskId={}", taskId, e);
            return AsyncImageResult.failed(taskId, e.getMessage());
        }
    }
}
