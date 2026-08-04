package com.xuejiai.aaf.module.ai.aigc.media.vo;

import java.time.LocalDateTime;

public record AigcAssetCollectionVO(
        Long id,
        Integer version,
        String name,
        String collectionType,
        String description,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
