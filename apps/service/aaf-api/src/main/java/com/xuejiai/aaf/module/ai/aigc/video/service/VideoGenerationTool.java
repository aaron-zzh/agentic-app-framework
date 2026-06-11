package com.xuejiai.aaf.module.ai.aigc.video.service;

import java.util.function.Function;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import com.xuejiai.aaf.framework.intelligent.ai.video.VideoGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.video.VideoGenerationService.TextToVideoRequest;
import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaAssetType;
import com.xuejiai.aaf.module.ai.aigc.media.service.MediaAssetService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.SaveFromGenerationDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 视频生成工具 — 注册为 Spring AI Function，可被对话中的 AI 调用。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class VideoGenerationTool {

    private final VideoGenerationService videoGenerationService;
    private final MediaAssetService mediaAssetService;

    public record Request(
            String prompt, Integer durationSeconds, String resolution, String ratio) {}

    public record Response(String taskId, String status, String message) {}

    @Bean
    @Description(
            "根据用户描述生成视频。参数：prompt(必填)-视频描述, durationSeconds(可选)-时长3~15秒, resolution(可选)-720P/1080P, ratio(可选)-16:9/9:16/1:1")
    public Function<Request, Response> generateVideo() {
        return request -> {
            log.info("对话触发视频生成: prompt={}", request.prompt());
            try {
                var videoRequest =
                        new TextToVideoRequest(
                                request.prompt(),
                                null,
                                request.resolution(),
                                request.ratio(),
                                request.durationSeconds(),
                                null);
                var taskId = videoGenerationService.submitTextToVideo(videoRequest);

                // 自动保存到素材库（视频为异步任务，先记录 taskId）
                try {
                    var saveDto =
                            new SaveFromGenerationDTO(
                                    null,
                                    MediaAssetType.VIDEO,
                                    "pending://" + taskId,
                                    null,
                                    "{\"prompt\":\"%s\",\"taskId\":\"%s\"}"
                                            .formatted(
                                                    request.prompt().replace("\"", "\\\""), taskId),
                                    null,
                                    null,
                                    null,
                                    null,
                                    true,
                                    null,
                                    null);
                    mediaAssetService.saveFromGeneration(0L, saveDto);
                } catch (Exception e) {
                    log.warn("自动保存素材库失败: {}", e.getMessage());
                }

                return new Response(taskId, "PENDING", "视频生成任务已提交，预计1-5分钟完成");
            } catch (Exception e) {
                log.error("对话生成视频失败: {}", e.getMessage(), e);
                return new Response(null, "FAILED", "生成失败: " + e.getMessage());
            }
        };
    }
}
