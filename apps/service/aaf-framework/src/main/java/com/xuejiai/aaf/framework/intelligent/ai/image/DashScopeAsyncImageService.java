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
 * 百炼异步图像生成服务（保留示例，供旧路径 AiImageService 使用）。
 *
 * <p>使用 {@link ImageSynthesis#asyncCall} 提交任务，返回第三方 taskId， 由调用方轮询 {@link #queryTask} 获取结果。
 *
 * <p>新路径（AigcTask）已统一使用同步 {@link DashScopeImageGenerationService}， 本类仅作为异步模式的保留示例供旧路径（AiImage
 * 表）继续使用。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.ai.dashscope.api-key", matchIfMissing = false)
public class DashScopeAsyncImageService implements AsyncImageGenerationService {

    private static final String DEFAULT_MODEL = "qwen-image-plus";

    private final String apiKey;
    private final ImageSynthesis imageSynthesis = new ImageSynthesis();

    public DashScopeAsyncImageService(@Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String submitTask(AsyncImageRequest request) {
        try {
            String model = request.modelId() != null ? request.modelId() : DEFAULT_MODEL;
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
            log.info("[DashScopeAsync] 任务提交: model={}, taskId={}", model, taskId);
            return taskId;
        } catch (ApiException | NoApiKeyException e) {
            log.error("[DashScopeAsync] 提交失败: prompt={}", request.prompt(), e);
            throw new RuntimeException("异步图像任务提交失败: " + e.getMessage(), e);
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
                    log.info("[DashScopeAsync] 任务完成: taskId={}, url={}", taskId, url);
                    yield AsyncImageResult.succeeded(taskId, url);
                }
                case "FAILED" -> {
                    String msg = result.getOutput().getMessage();
                    log.warn("[DashScopeAsync] 任务失败: taskId={}, msg={}", taskId, msg);
                    yield AsyncImageResult.failed(taskId, msg);
                }
                default -> AsyncImageResult.pending(taskId);
            };
        } catch (ApiException | NoApiKeyException e) {
            log.error("[DashScopeAsync] 查询失败: taskId={}", taskId, e);
            return AsyncImageResult.failed(taskId, e.getMessage());
        }
    }
}
