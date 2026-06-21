package com.xuejiai.aaf.module.ai.aigc.video.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 视频模板更新 DTO。 */
public record VideoTemplateUpdateDTO(
        @Schema(description = "模板名称") String name,
        @Schema(description = "模板类型") String type,
        @Schema(description = "模板参数（JSON）") String params,
        @Schema(description = "预览视频 URL") String previewUrl,
        @Schema(description = "缩略图 URL") String thumbnailUrl) {}
