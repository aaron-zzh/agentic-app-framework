package com.xuejiai.aaf.module.ai.aigc.timeline.vo;

import java.time.LocalDateTime;

public record AigcStoryboardExportVO(
        Long id,
        Long projectId,
        Integer sourceRevisionNo,
        Long deliverableObjectId,
        Long exportMediaVersionId,
        String exportFormat,
        LocalDateTime createTime) {}
