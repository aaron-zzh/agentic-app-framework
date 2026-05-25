package com.xuejiai.aaf.module.aigc.vo;

import java.math.BigDecimal;

import com.xuejiai.aaf.module.aigc.enums.MediaAssetType;

import jakarta.validation.constraints.NotBlank;

/** 从生成结果保存到素材库的请求。 */
public record SaveFromGenerationDTO(
        String name,
        MediaAssetType type,
        @NotBlank String url,
        String thumbnailUrl,
        String generationParams,
        Integer width,
        Integer height,
        BigDecimal duration) {}
