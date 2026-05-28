package com.xuejiai.aaf.module.aigc.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 素材分类 Response VO（树形结构）。 */
public record MediaCategoryVO(
        @Schema(description = "分类 ID", example = "1") Long id,
        @Schema(description = "分类名称", example = "风景图片") String name,
        @Schema(description = "父分类 ID", example = "0") Long parentId,
        @Schema(description = "排序序号", example = "0") Integer sortOrder,
        @Schema(description = "子分类列表") List<MediaCategoryVO> children) {}
