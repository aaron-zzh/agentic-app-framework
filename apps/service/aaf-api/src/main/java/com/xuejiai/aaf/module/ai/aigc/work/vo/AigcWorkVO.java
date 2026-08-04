package com.xuejiai.aaf.module.ai.aigc.work.vo;

import java.time.LocalDateTime;

public record AigcWorkVO(
        Long id,
        Integer version,
        Long projectId,
        Long deliverableObjectId,
        Long adoptedObjectVersionId,
        String title,
        Long coverMediaVersionId,
        String status,
        String visibility,
        Long userId,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
