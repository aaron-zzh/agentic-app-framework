package com.xuejiai.aaf.module.ai.aigc.video.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.ai.video.DashScopeVideoGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.video.VideoGenerationService.*;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.intelligent.core.registry.AiServiceRegistry;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * AIGC 视频生成与编辑接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "AIGC 视频")
@RestController
@RequestMapping("/api/aigc/video")
@RequiredArgsConstructor
public class VideoGenerationController {

    private final AiServiceRegistry aiServiceRegistry;
    private final DashScopeVideoGenerationService dashScopeVideoService;
    private final CapabilityRouter capabilityRouter;
    private final OperatorContext operatorContext;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "文生视频")
    @PostMapping("/text-to-video")
    public Result<String> textToVideo(@Valid @RequestBody TextToVideoDTO dto) {
        var mockVal = getMockVideoUrl();
        if (mockVal != null) return Result.success(mockVal);
        var userId = operatorContext.currentUserId().orElse(null);
        var ctx =
                CapabilityRoutingContext.of(
                        userId, CapabilityRoutingContext.CAP_VIDEO_GEN, dto.model());
        var aiModel = capabilityRouter.resolve(ctx);
        var service =
                aiServiceRegistry.get(
                        com.xuejiai.aaf.framework.intelligent.ai.video.VideoGenerationService.class,
                        aiModel);
        var request =
                new TextToVideoRequest(
                        dto.prompt(),
                        aiModel,
                        dto.resolution(),
                        dto.ratio(),
                        dto.duration(),
                        dto.seed(),
                        dto.promptExtend());
        return Result.success(service.submitTextToVideo(request));
    }

    @Operation(summary = "图生视频（首帧）")
    @PostMapping("/image-to-video")
    public Result<String> imageToVideo(@Valid @RequestBody ImageToVideoDTO dto) {
        var mockVal = getMockVideoUrl();
        if (mockVal != null) return Result.success(mockVal);
        var userId = operatorContext.currentUserId().orElse(null);
        var ctx =
                CapabilityRoutingContext.of(
                        userId, CapabilityRoutingContext.CAP_VIDEO_GEN, dto.model());
        var aiModel = capabilityRouter.resolve(ctx);
        var request =
                new ImageToVideoRequest(
                        dto.prompt(),
                        dto.firstFrameUrl(),
                        dto.model(),
                        dto.resolution(),
                        dto.duration(),
                        dto.seed());
        return Result.success(
                aiServiceRegistry
                        .get(
                                com.xuejiai.aaf.framework.intelligent.ai.video
                                        .VideoGenerationService.class,
                                aiModel)
                        .submitImageToVideo(request));
    }

    @Operation(summary = "视频编辑")
    @PostMapping("/edit")
    public Result<String> videoEdit(@Valid @RequestBody VideoEditDTO dto) {
        var mockVal = getMockVideoUrl();
        if (mockVal != null) return Result.success(mockVal);
        var userId = operatorContext.currentUserId().orElse(null);
        var ctx =
                CapabilityRoutingContext.of(
                        userId, CapabilityRoutingContext.CAP_VIDEO_GEN, dto.model());
        var aiModel = capabilityRouter.resolve(ctx);
        var request =
                new VideoEditApiRequest(
                        dto.prompt(),
                        dto.videoUrl(),
                        dto.referenceImageUrls(),
                        dto.model(),
                        dto.resolution(),
                        dto.audioSetting(),
                        dto.seed());
        return Result.success(
                aiServiceRegistry
                        .get(
                                com.xuejiai.aaf.framework.intelligent.ai.video
                                        .VideoGenerationService.class,
                                aiModel)
                        .submitVideoEdit(request));
    }

    /** 判断是否开启 mock 并返回 video 类型的固定 URL，未开启时返回 null。 */
    private String getMockVideoUrl() {
        if (!systemConfigService.getBoolean(SysConfigKeys.Aigc.MOCK_ENABLED, false)) return null;
        var json = systemConfigService.getString(SysConfigKeys.Aigc.MOCK_DATA);
        if (json == null || json.isBlank()) return "";
        try {
            var map = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
            return map.getOrDefault("video", "");
        } catch (Exception e) {
            return "";
        }
    }

    @Operation(summary = "查询视频任务状态")
    @GetMapping("/task/{taskId}")
    public Result<VideoTaskResult> queryTask(@PathVariable String taskId) {
        return Result.success(dashScopeVideoService.query(taskId));
    }

    // === DTO Records ===

    /** 文生视频 Request DTO。 */
    public record TextToVideoDTO(
            @Schema(description = "视频描述提示词", example = "蓝天白云下的草原") @NotBlank String prompt,
            @Schema(description = "模型 ID（为空则走系统路由链）", example = "dashscope:wan2.7-t2v-2026-04-25")
                    String model,
            @Schema(description = "分辨率", example = "720P") String resolution,
            @Schema(description = "画面比例", example = "16:9") String ratio,
            @Schema(description = "时长（秒）", example = "5") Integer duration,
            @Schema(description = "随机种子", example = "12345") Integer seed,
            @Schema(description = "是否开启提示词扩写（wan2 系列支持）", example = "true") Boolean promptExtend) {}

    /** 图生视频 Request DTO。 */
    public record ImageToVideoDTO(
            @Schema(description = "视频描述提示词", example = "让画面动起来") String prompt,
            @Schema(description = "首帧图片 URL", example = "https://cdn.example.com/first.png")
                    @NotBlank
                    String firstFrameUrl,
            @Schema(description = "模型名称", example = "happyhorse-1.0-i2v") String model,
            @Schema(description = "分辨率", example = "1080P") String resolution,
            @Schema(description = "时长（秒）", example = "5") Integer duration,
            @Schema(description = "随机种子", example = "12345") Integer seed) {}

    /** 视频编辑 Request DTO。 */
    public record VideoEditDTO(
            @Schema(description = "编辑描述提示词", example = "将背景替换为海滩") @NotBlank String prompt,
            @Schema(description = "原始视频 URL", example = "https://cdn.example.com/video.mp4")
                    @NotBlank
                    String videoUrl,
            @Schema(description = "参考图片 URL 列表") List<String> referenceImageUrls,
            @Schema(description = "模型名称", example = "happyhorse-1.0-video-edit") String model,
            @Schema(description = "分辨率", example = "1080P") String resolution,
            @Schema(description = "音频设置", example = "keep") String audioSetting,
            @Schema(description = "随机种子", example = "12345") Integer seed) {}

    /**
     * doubao-seedance 富内容生成接口——支持文字 + 参考图 + 参考视频 + 参考音频同时传入。
     *
     * <p>模型示例：{@code volcengine:doubao-seedance-2-0-260128}
     */
    @Operation(summary = "Seedance 富内容视频生成（支持参考视频/音频）")
    @PostMapping("/seedance/rich")
    public Result<String> seedanceRich(@Valid @RequestBody SeedanceRichDTO dto) {
        var mockVal = getMockVideoUrl();
        if (mockVal != null) return Result.success(mockVal);

        var userId = operatorContext.currentUserId().orElse(null);
        var ctx =
                CapabilityRoutingContext.of(
                        userId, CapabilityRoutingContext.CAP_VIDEO_GEN, dto.model());
        var aiModel = capabilityRouter.resolve(ctx);

        var service =
                aiServiceRegistry.get(
                        com.xuejiai.aaf.framework.intelligent.ai.video.VideoGenerationService.class,
                        aiModel);
        if (!(service
                instanceof
                com.xuejiai.aaf.framework.intelligent.ai.video.DoubaoVideoGenerationService
                        doubao)) {
            throw new IllegalArgumentException("seedance/rich 接口仅支持 volcengine 供应商模型");
        }

        return Result.success(
                doubao.submitRich(
                        aiModel,
                        dto.prompt(),
                        dto.referenceImages(),
                        dto.referenceVideos(),
                        dto.referenceAudios(),
                        dto.ratio(),
                        dto.duration(),
                        Boolean.TRUE.equals(dto.generateAudio())));
    }

    /** doubao-seedance 富内容生成 DTO。 */
    public record SeedanceRichDTO(
            @Schema(description = "视频描述提示词") @NotBlank String prompt,
            @Schema(description = "模型 ID", example = "volcengine:doubao-seedance-2-0-260128")
                    String model,
            @Schema(description = "参考图片 URL 列表（role=reference_image）") List<String> referenceImages,
            @Schema(description = "参考视频 URL 列表（role=reference_video）") List<String> referenceVideos,
            @Schema(description = "参考音频 URL 列表（role=reference_audio）") List<String> referenceAudios,
            @Schema(description = "画面比例", example = "16:9") String ratio,
            @Schema(description = "时长（秒）", example = "11") Integer duration,
            @Schema(description = "是否生成配套音频", example = "true") Boolean generateAudio) {}
}
