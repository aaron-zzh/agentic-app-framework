package com.xuejiai.aaf.module.aigc.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 素材分类创建 Request DTO。 */
public record MediaCategoryCreateDTO(
        @Schema(description = "分类名称", example = "风景图片") @NotBlank String name,
        @Schema(description = "父分类 ID，顶级分类为 null", example = "1") Long parentId,
        @Schema(description = "排序序号", example = "0") Integer sortOrder) {}
