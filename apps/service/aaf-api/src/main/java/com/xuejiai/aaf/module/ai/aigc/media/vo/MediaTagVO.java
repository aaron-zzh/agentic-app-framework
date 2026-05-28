package com.xuejiai.aaf.module.ai.aigc.media.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 素材标签 Response VO。 */
public record MediaTagVO(
        @Schema(description = "标签 ID", example = "1") Long id,
        @Schema(description = "标签名称", example = "风景") String name,
        @Schema(description = "标签颜色（十六进制）", example = "#FF5733") String color) {}
