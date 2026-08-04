package com.xuejiai.aaf.module.ai.aigc.media.vo;

import java.time.LocalDateTime;

/** Asset 响应；media 为同一素材引用的动态投影。 */
public record AssetVO(
        Long id,
        Long mediaId,
        Long categoryId,
        String scope,
        String copyrightInfo,
        String status,
        Integer usageCount,
        MediaVO media,
        LocalDateTime createTime) {}
