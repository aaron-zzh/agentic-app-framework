package com.xuejiai.aaf.module.aigc.vo;

import jakarta.validation.constraints.NotBlank;

/** 素材分类创建请求。 */
public record MediaCategoryCreateDTO(
        @NotBlank String name,
        Long parentId,
        Integer sortOrder) {}
