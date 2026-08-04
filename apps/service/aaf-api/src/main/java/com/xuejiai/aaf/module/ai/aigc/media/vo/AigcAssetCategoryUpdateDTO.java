package com.xuejiai.aaf.module.ai.aigc.media.vo;

import jakarta.validation.constraints.NotBlank;

public record AigcAssetCategoryUpdateDTO(@NotBlank String name, Long parentId, Integer sortOrder) {}
