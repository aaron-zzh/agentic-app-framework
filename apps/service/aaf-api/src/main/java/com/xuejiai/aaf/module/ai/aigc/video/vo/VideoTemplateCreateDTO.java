package com.xuejiai.aaf.module.ai.aigc.video.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 视频模板创建 DTO。 */
public record VideoTemplateCreateDTO(
        @Schema(description = "模板名称") @NotBlank String name,
        @Schema(description = "模板类型：INTRO/OUTRO/TRANSITION/SUBTITLE") @NotBlank String type,
        @Schema(description = "模板参数（JSON）") String params,
        @Schema(description = "预览视频 URL") String previewUrl,
        @Schema(description = "缩略图 URL") String thumbnailUrl) {}
