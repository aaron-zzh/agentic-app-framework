package com.xuejiai.aaf.module.ai.aigc.media.vo;

import java.time.LocalDateTime;

public record AigcAssetCategoryVO(
        Long id,
        Integer version,
        String name,
        Long parentId,
        Integer sortOrder,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
