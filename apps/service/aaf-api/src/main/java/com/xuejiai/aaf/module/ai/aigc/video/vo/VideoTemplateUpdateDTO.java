package com.xuejiai.aaf.module.ai.aigc.video.vo;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/** 视频模板更新 DTO。 */
public record VideoTemplateUpdateDTO(
        @Schema(description = "模板名称") String name,
        @Schema(description = "模板类型") String type,
        @Schema(description = "模板参数") Map<String, Object> params,
        @Schema(description = "预览视频媒体版本 ID") Long previewMediaVersionId,
        @Schema(description = "缩略图媒体版本 ID") Long thumbnailMediaVersionId) {}
