package com.xuejiai.aaf.framework.intelligent.ai.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.alibaba.dashscope.aigc.videosynthesis.VideoSynthesis;
import com.alibaba.dashscope.aigc.videosynthesis.VideoSynthesisParam;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 DashScope SDK 的视频生成实现（异步任务模式）。
 *
 * <p>支持模型：wan2.6-i2v-flash、wan2.7-i2v-plus 等。
 * <p>启用条件：配置 {@code spring.ai.dashscope.api-key}
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.ai.dashscope.api-key", matchIfMissing = false)
public class DashScopeVideoGenerationService implements VideoGenerationService {

    private static final String DEFAULT_MODEL = "wan2.6-i2v-flash";

    private final String apiKey;
    private final VideoSynthesis videoSynthesis = new VideoSynthesis();

    public DashScopeVideoGenerationService(
            @Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String submit(VideoGenerationRequest request) {
        try {
            String model = (request.model() != null && !request.model().isBlank())
                    ? request.model() : DEFAULT_MODEL;

            var param = VideoSynthesisParam.builder()
                    .apiKey(apiKey)
                    .model(model)
                    .prompt(request.prompt())
                    .build();

            var result = videoSynthesis.asyncCall(param);
            String taskId = result.getOutput().getTaskId();
            log.info("[DashScopeVideo] 任务提交成功: model={}, taskId={}", model, taskId);
            return taskId;
        } catch (ApiException | NoApiKeyException | com.alibaba.dashscope.exception.InputRequiredException e) {
            log.error("[DashScopeVideo] 任务提交失败: prompt={}", request.prompt(), e);
            throw new RuntimeException("视频生成任务提交失败: " + e.getMessage(), e);
        }
    }

    @Override
    public VideoResult query(String taskId) {
        try {
            var result = videoSynthesis.fetch(taskId, apiKey);
            String status = result.getOutput().getTaskStatus();
            return switch (status) {
                case "SUCCEEDED" -> {
                    String url = result.getOutput().getVideoUrl();
                    log.info("[DashScopeVideo] 任务完成: taskId={}, url={}", taskId, url);
                    yield new VideoResult(taskId, VideoResult.Status.SUCCEEDED, url);
                }
                case "FAILED" -> {
                    log.warn("[DashScopeVideo] 任务失败: taskId={}", taskId);
                    yield new VideoResult(taskId, VideoResult.Status.FAILED, null);
                }
                case "RUNNING" -> new VideoResult(taskId, VideoResult.Status.PROCESSING, null);
                default -> new VideoResult(taskId, VideoResult.Status.PENDING, null);
            };
        } catch (ApiException | NoApiKeyException e) {
            log.error("[DashScopeVideo] 查询任务失败: taskId={}", taskId, e);
            return new VideoResult(taskId, VideoResult.Status.FAILED, null);
        }
    }
}
