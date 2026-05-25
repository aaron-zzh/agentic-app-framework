package com.xuejiai.aaf.module.aigc.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.ai.media.VideoGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.media.VideoGenerationService.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/** 视频生成与编辑接口（基于 HappyHorse 模型）。 */
@Tag(name = "AIGC 视频")
@RestController
@RequestMapping("/api/aigc/video")
@RequiredArgsConstructor
public class VideoGenerationController {

    private final VideoGenerationService videoGenerationService;

    @Operation(summary = "文生视频")
    @PostMapping("/text-to-video")
    public Result<String> textToVideo(@Valid @RequestBody TextToVideoDTO dto) {
        var request = new TextToVideoRequest(
                dto.prompt(), dto.model(), dto.resolution(), dto.ratio(), dto.duration(), dto.seed());
        return Result.success(videoGenerationService.submitTextToVideo(request));
    }

    @Operation(summary = "图生视频（首帧）")
    @PostMapping("/image-to-video")
    public Result<String> imageToVideo(@Valid @RequestBody ImageToVideoDTO dto) {
        var request = new ImageToVideoRequest(
                dto.prompt(), dto.firstFrameUrl(), dto.model(), dto.resolution(), dto.duration(), dto.seed());
        return Result.success(videoGenerationService.submitImageToVideo(request));
    }

    @Operation(summary = "视频编辑")
    @PostMapping("/edit")
    public Result<String> videoEdit(@Valid @RequestBody VideoEditDTO dto) {
        var request = new VideoEditApiRequest(
                dto.prompt(), dto.videoUrl(), dto.referenceImageUrls(),
                dto.model(), dto.resolution(), dto.audioSetting(), dto.seed());
        return Result.success(videoGenerationService.submitVideoEdit(request));
    }

    @Operation(summary = "查询视频任务状态")
    @GetMapping("/task/{taskId}")
    public Result<VideoTaskResult> queryTask(@PathVariable String taskId) {
        return Result.success(videoGenerationService.query(taskId));
    }

    // === DTO Records ===

    public record TextToVideoDTO(
            @NotBlank String prompt,
            String model,
            String resolution,
            String ratio,
            Integer duration,
            Integer seed) {}

    public record ImageToVideoDTO(
            String prompt,
            @NotBlank String firstFrameUrl,
            String model,
            String resolution,
            Integer duration,
            Integer seed) {}

    public record VideoEditDTO(
            @NotBlank String prompt,
            @NotBlank String videoUrl,
            List<String> referenceImageUrls,
            String model,
            String resolution,
            String audioSetting,
            Integer seed) {}
}
