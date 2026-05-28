package com.xuejiai.aaf.module.aigc.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 素材更新 Request DTO。 */
public record MediaAssetUpdateDTO(
        @Schema(description = "素材名称", example = "新名称") String name,
        @Schema(description = "标签，逗号分隔", example = "风景,AI生成") String tags,
        @Schema(description = "分类 ID", example = "2") Long categoryId) {}
