package com.xuejiai.aaf.module.ai.aigc.media.vo;

import java.time.LocalDateTime;

/** AIGC 资产响应，media 为同一稳定媒体的动态投影。 */
public record AigcAssetVO(
        Long id,
        Long mediaId,
        Long categoryId,
        String scope,
        String copyrightInfo,
        String status,
        Integer usageCount,
        AigcMediaVO media,
        LocalDateTime createTime) {}
