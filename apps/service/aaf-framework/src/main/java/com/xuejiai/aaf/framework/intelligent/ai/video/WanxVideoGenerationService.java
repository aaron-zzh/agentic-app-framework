package com.xuejiai.aaf.framework.intelligent.ai.video;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.alibaba.dashscope.aigc.videosynthesis.VideoSynthesis;
import com.alibaba.dashscope.aigc.videosynthesis.VideoSynthesisParam;
import com.alibaba.dashscope.utils.Constants;

import com.xuejiai.aaf.framework.intelligent.ai.video.vo.ImageToVideoRequest;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.ReferenceToVideoRequest;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.TextToVideoRequest;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.VideoEditApiRequest;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.VideoTaskResult;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于阿里云百炼 SDK（VideoSynthesis）的视频生成实现。
 *
 * <p>支持模型（wan2.x 系列）：
 *
 * <ul>
 *   <li>wan2.7-t2v-2026-04-25 — 文生视频
 *   <li>wan2.1-t2v-turbo — 文生视频快速版
 * </ul>
 *
 * <p>happyhorse 系列请使用 {@link DashScopeVideoGenerationService}。
 */
@Slf4j
@Service("wanxVideoGenerationService")
@ConditionalOnProperty(name = "spring.ai.dashscope.api-key", matchIfMissing = false)
public class WanxVideoGenerationService implements VideoGenerationService {

    static {
        Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";
    }

    private final VideoSynthesis videoSynthesis = new VideoSynthesis();
    private final DashScopeVideoGenerationService dashScopeService;

    public WanxVideoGenerationService(DashScopeVideoGenerationService dashScopeService) {
        this.dashScopeService = dashScopeService;
    }

    @Override
    public String submitTextToVideo(TextToVideoRequest request) {
        var model = request.getResolvedModel().getModelName();
        var apiKey = request.getResolvedModel().effectiveApiKey();

        var paramBuilder =
                VideoSynthesisParam.builder()
                        .apiKey(apiKey)
                        .model(model)
                        .prompt(request.getPrompt())
                        .watermark(false);

        if (request.getResolution() != null) paramBuilder.resolution(request.getResolution());
        if (request.getRatio() != null) paramBuilder.ratio(request.getRatio());
        if (request.getDuration() != null) paramBuilder.duration(request.getDuration());
        if (request.getPromptExtend() != null) paramBuilder.promptExtend(request.getPromptExtend());

        try {
            var result = videoSynthesis.asyncCall(paramBuilder.build());
            var taskId = result.getOutput().getTaskId();
            log.info("[Wanx] 任务提交成功: model={}, taskId={}", model, taskId);
            return taskId;
        } catch (Exception e) {
            log.error("[Wanx] 任务提交失败: model={}", model, e);
            throw new RuntimeException("wan2 视频生成任务提交失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String submitImageToVideo(ImageToVideoRequest request) {
        throw new UnsupportedOperationException("wan2 图生视频暂未实现，请使用 happyhorse-1.0-i2v");
    }

    @Override
    public String submitReferenceToVideo(ReferenceToVideoRequest request) {
        throw new UnsupportedOperationException("wan2 参考生视频暂未实现");
    }

    @Override
    public String submitVideoEdit(VideoEditApiRequest request) {
        throw new UnsupportedOperationException("wan2 视频编辑暂未实现");
    }

    @Override
    public VideoTaskResult query(String taskId) {
        // wan2 任务查询与 happyhorse 共用同一套 DashScope task API
        return dashScopeService.query(taskId);
    }
}
