package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.time.LocalDateTime;
import java.util.List;

public record AigcProjectRevisionVO(
        Long id,
        Long projectId,
        Integer revisionNo,
        List<Long> changedObjectIds,
        List<Long> changedRelationIds,
        String actorType,
        Long actorId,
        Long sourceExecutionRunId,
        String summary,
        LocalDateTime createTime) {}
