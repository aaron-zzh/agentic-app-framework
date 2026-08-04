package com.xuejiai.aaf.module.ai.aigc.media.vo;

import java.time.LocalDateTime;

public record AigcAssetTagVO(
        Long id,
        Integer version,
        String name,
        String color,
        Integer usageCount,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
