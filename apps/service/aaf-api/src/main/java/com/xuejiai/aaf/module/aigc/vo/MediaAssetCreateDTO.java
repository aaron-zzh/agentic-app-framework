package com.xuejiai.aaf.module.aigc.vo;

import java.math.BigDecimal;

import com.xuejiai.aaf.module.aigc.enums.MediaAssetType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 素材创建请求。 */
public record MediaAssetCreateDTO(
        @NotBlank String name,
        @NotNull MediaAssetType type,
        @NotBlank String url,
        String thumbnailUrl,
        Long size,
        Integer width,
        Integer height,
        BigDecimal duration,
        String generationParams,
        String tags,
        Long categoryId) {}
