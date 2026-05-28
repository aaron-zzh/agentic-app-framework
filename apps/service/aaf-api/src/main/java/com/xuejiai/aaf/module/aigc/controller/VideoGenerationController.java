package com.xuejiai.aaf.module.aigc.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.ai.video.VideoGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.video.VideoGenerationService.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * AIGC 视频生成与编辑接口（基于 HappyHorse 模型）。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "AIGC 视频")
@RestController
@RequestMapping("/api/aigc/video")
@RequiredArgsConstructor
public class VideoGenerationController {

    private final VideoGenerationService videoGenerationService;

    @Operation(summary = "文生视频")
    @PostMapping("/text-to-video")
    public Result<String> textToVideo(@Valid @RequestBody TextToVideoDTO dto) {
        var request =
                new TextToVideoRequest(
                        dto.prompt(),
                        dto.model(),
                        dto.resolution(),
                        dto.ratio(),
                        dto.duration(),
                        dto.seed());
        return Result.success(videoGenerationService.submitTextToVideo(request));
    }

    @Operation(summary = "图生视频（首帧）")
    @PostMapping("/image-to-video")
    public Result<String> imageToVideo(@Valid @RequestBody ImageToVideoDTO dto) {
        var request =
                new ImageToVideoRequest(
                        dto.prompt(),
                        dto.firstFrameUrl(),
                        dto.model(),
                        dto.resolution(),
                        dto.duration(),
                        dto.seed());
        return Result.success(videoGenerationService.submitImageToVideo(request));
    }

    @Operation(summary = "视频编辑")
    @PostMapping("/edit")
    public Result<String> videoEdit(@Valid @RequestBody VideoEditDTO dto) {
        var request =
                new VideoEditApiRequest(
                        dto.prompt(),
                        dto.videoUrl(),
                        dto.referenceImageUrls(),
                        dto.model(),
                        dto.resolution(),
                        dto.audioSetting(),
                        dto.seed());
        return Result.success(videoGenerationService.submitVideoEdit(request));
    }

    @Operation(summary = "查询视频任务状态")
    @GetMapping("/task/{taskId}")
    public Result<VideoTaskResult> queryTask(@PathVariable String taskId) {
        return Result.success(videoGenerationService.query(taskId));
    }

    // === DTO Records ===

    /** 文生视频 Request DTO。 */
    public record TextToVideoDTO(
            @Schema(description = "视频描述提示词", example = "蓝天白云下的草原") @NotBlank String prompt,
            @Schema(description = "模型名称", example = "happy-horse-v1") String model,
            @Schema(description = "分辨率", example = "1080P") String resolution,
            @Schema(description = "画面比例", example = "16:9") String ratio,
            @Schema(description = "时长（秒）", example = "5") Integer duration,
            @Schema(description = "随机种子", example = "12345") Integer seed) {}

    /** 图生视频 Request DTO。 */
    public record ImageToVideoDTO(
            @Schema(description = "视频描述提示词", example = "让画面动起来") String prompt,
            @Schema(description = "首帧图片 URL", example = "https://cdn.example.com/first.png")
                    @NotBlank
                    String firstFrameUrl,
            @Schema(description = "模型名称", example = "happy-horse-v1") String model,
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
            @Schema(description = "模型名称", example = "happy-horse-v1") String model,
            @Schema(description = "分辨率", example = "1080P") String resolution,
            @Schema(description = "音频设置", example = "keep") String audioSetting,
            @Schema(description = "随机种子", example = "12345") Integer seed) {}
}
