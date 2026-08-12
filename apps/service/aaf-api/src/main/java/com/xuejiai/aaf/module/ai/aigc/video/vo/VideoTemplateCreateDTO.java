package com.xuejiai.aaf.module.ai.aigc.video.vo;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 视频模板创建 DTO。 */
public record VideoTemplateCreateDTO(
        @Schema(description = "模板名称") @NotBlank String name,
        @Schema(description = "模板类型：INTRO/OUTRO/TRANSITION/SUBTITLE") @NotBlank String type,
        @Schema(description = "模板参数") Map<String, Object> params,
        @Schema(description = "预览视频媒体版本 ID") Long previewMediaVersionId,
        @Schema(description = "缩略图媒体版本 ID") Long thumbnailMediaVersionId) {}
