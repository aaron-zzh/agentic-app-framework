package com.xuejiai.aaf.module.ai.aigc.model3d.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.ai.model3d.Model3dGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.model3d.Model3dGenerationService.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/**
 * AIGC 3D 模型生成接口（支持百炼 Tripo + Meshy）。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "AIGC 3D 模型")
@RestController
@RequestMapping("/api/aigc/model3d")
@RequiredArgsConstructor
public class Model3dController {

    private final Model3dGenerationService model3dGenerationService;

    @Operation(summary = "文生 3D")
    @PostMapping("/text-to-3d")
    public Result<String> textTo3d(@Valid @RequestBody TextTo3dDTO dto) {
        var request = new TextTo3dRequest(dto.prompt(), dto.textureQuality(), dto.pbr());
        return Result.success(model3dGenerationService.submitTextTo3d(request));
    }

    @Operation(summary = "单图生 3D")
    @PostMapping("/image-to-3d")
    public Result<String> imageTo3d(@Valid @RequestBody ImageTo3dDTO dto) {
        var request = new ImageTo3dRequest(dto.imageUrl(), dto.textureQuality(), dto.pbr());
        return Result.success(model3dGenerationService.submitImageTo3d(request));
    }

    @Operation(summary = "多图生 3D（四视角：前/左/后/右）")
    @PostMapping("/multi-image-to-3d")
    public Result<String> multiImageTo3d(@Valid @RequestBody MultiImageTo3dDTO dto) {
        var images = dto.images().stream()
                .map(img -> img == null || img.fileToken() == null
                        ? null
                        : new ImageInput(img.type(), img.fileToken()))
                .toList();
        var request = new MultiImageTo3dRequest(images, dto.textureQuality(), dto.pbr());
        return Result.success(model3dGenerationService.submitMultiImageTo3d(request));
    }

    @Operation(summary = "查询 3D 任务状态")
    @GetMapping("/task/{taskId}")
    public Result<Model3dTaskResult> queryTask(@PathVariable String taskId) {
        return Result.success(model3dGenerationService.query(taskId));
    }

    // === DTO Records ===

    public record TextTo3dDTO(
            @Schema(description = "3D 模型描述提示词") @NotBlank String prompt,
            @Schema(description = "贴图质量：standard/detailed") String textureQuality,
            @Schema(description = "是否生成 PBR 材质") Boolean pbr) {}

    public record ImageTo3dDTO(
            @Schema(description = "参考图片 URL") @NotBlank String imageUrl,
            @Schema(description = "贴图质量：standard/detailed") String textureQuality,
            @Schema(description = "是否生成 PBR 材质") Boolean pbr) {}

    public record MultiImageTo3dDTO(
            @Schema(description = "四视角图片列表（前/左/后/右），不需要的视角传 null") @NotNull
                    List<ImageInputDTO> images,
            @Schema(description = "贴图质量：standard/detailed") String textureQuality,
            @Schema(description = "是否生成 PBR 材质") Boolean pbr) {}

    public record ImageInputDTO(
            @Schema(description = "图片格式：jpeg/png") String type,
            @Schema(description = "图片 URL") String fileToken) {}
}
