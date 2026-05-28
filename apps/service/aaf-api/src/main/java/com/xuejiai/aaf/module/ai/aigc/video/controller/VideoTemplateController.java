package com.xuejiai.aaf.module.ai.aigc.video.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.ai.aigc.video.domain.VideoTemplate;
import com.xuejiai.aaf.module.ai.aigc.video.service.VideoTemplateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * AIGC 视频模板管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "AIGC 视频模板")
@RestController
@RequestMapping("/api/aigc/video/templates")
@RequiredArgsConstructor
public class VideoTemplateController {

    private final VideoTemplateService templateService;

    /** 创建视频模板 Request DTO。 */
    public record VideoTemplateCreateDTO(
            @Schema(description = "模板名称", example = "片头模板") @NotBlank String name,
            @Schema(description = "模板类型", example = "INTRO") @NotBlank String type,
            @Schema(description = "模板参数（JSON）", example = "{}") String params,
            @Schema(description = "预览视频 URL") String previewUrl,
            @Schema(description = "缩略图 URL") String thumbnailUrl) {}

    @Operation(summary = "分页查询视频模板")
    @GetMapping
    public Result<Page<VideoTemplate>> page(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(templateService.page(type, PageRequest.of(page, size)));
    }

    @Operation(summary = "获取视频模板详情")
    @GetMapping("/{id}")
    public Result<VideoTemplate> getById(@PathVariable Long id) {
        return Result.success(templateService.getById(id));
    }

    @Operation(summary = "创建视频模板")
    @PostMapping
    public Result<VideoTemplate> create(@Valid @RequestBody VideoTemplateCreateDTO dto) {
        var template = new VideoTemplate();
        template.setName(dto.name());
        template.setType(dto.type());
        template.setParams(dto.params());
        template.setPreviewUrl(dto.previewUrl());
        template.setThumbnailUrl(dto.thumbnailUrl());
        return Result.success(templateService.create(template));
    }

    @Operation(summary = "删除视频模板")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return Result.success();
    }
}
