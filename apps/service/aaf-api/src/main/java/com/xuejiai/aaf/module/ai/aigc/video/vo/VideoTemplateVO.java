package com.xuejiai.aaf.module.ai.aigc.video.vo;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/** 视频模板响应 VO。 */
public record VideoTemplateVO(
        @Schema(description = "ID") Long id,
        @Schema(description = "模板名称") String name,
        @Schema(description = "模板类型：INTRO/OUTRO/TRANSITION/SUBTITLE") String type,
        @Schema(description = "模板参数") Map<String, Object> params,
        @Schema(description = "预览视频媒体版本 ID") Long previewMediaVersionId,
        @Schema(description = "缩略图媒体版本 ID") Long thumbnailMediaVersionId) {}
