package com.xuejiai.aaf.module.aigc.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.xuejiai.aaf.module.aigc.enums.MediaAssetType;

/** 素材响应 VO。 */
public record MediaAssetVO(
        Long id,
        String name,
        MediaAssetType type,
        String url,
        String thumbnailUrl,
        Long size,
        Integer width,
        Integer height,
        BigDecimal duration,
        String generationParams,
        String tags,
        Long categoryId,
        Long userId,
        Integer version,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
