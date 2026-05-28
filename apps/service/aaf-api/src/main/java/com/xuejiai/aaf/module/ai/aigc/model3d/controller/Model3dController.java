package com.xuejiai.aaf.module.ai.aigc.model3d.controller;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.ai.model3d.Model3dGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.model3d.Model3dGenerationService.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * AIGC 3D 模型生成接口（基于 Meshy API）。
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
        var request = new TextTo3dRequest(dto.prompt(), dto.style(), dto.format());
        return Result.success(model3dGenerationService.submitTextTo3d(request));
    }

    @Operation(summary = "图生 3D")
    @PostMapping("/image-to-3d")
    public Result<String> imageTo3d(@Valid @RequestBody ImageTo3dDTO dto) {
        var request = new ImageTo3dRequest(dto.imageUrl(), dto.format());
        return Result.success(model3dGenerationService.submitImageTo3d(request));
    }

    @Operation(summary = "查询 3D 任务状态")
    @GetMapping("/task/{taskId}")
    public Result<Model3dTaskResult> queryTask(@PathVariable String taskId) {
        return Result.success(model3dGenerationService.query(taskId));
    }

    // === DTO Records ===

    /** 文生 3D Request DTO。 */
    public record TextTo3dDTO(
            @Schema(description = "3D 模型描述提示词", example = "一把中世纪风格的宝剑") @NotBlank
                    String prompt,
            @Schema(description = "风格", example = "realistic") String style,
            @Schema(description = "输出格式", example = "glb") String format) {}

    /** 图生 3D Request DTO。 */
    public record ImageTo3dDTO(
            @Schema(description = "参考图片 URL", example = "https://cdn.example.com/ref.png")
                    @NotBlank
                    String imageUrl,
            @Schema(description = "输出格式", example = "glb") String format) {}
}
