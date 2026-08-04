package com.xuejiai.aaf.module.ai.aigc.media.vo;

import java.time.LocalDateTime;

public record AigcAssetCollectionItemVO(
        Long id,
        Long collectionId,
        Long assetId,
        String role,
        Integer sortOrder,
        LocalDateTime createTime) {}
