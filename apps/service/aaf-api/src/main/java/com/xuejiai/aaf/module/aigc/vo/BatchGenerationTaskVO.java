package com.xuejiai.aaf.module.aigc.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.module.aigc.enums.BatchTaskStatus;

/** 批量生成任务响应。 */
public record BatchGenerationTaskVO(
        Long id,
        BatchTaskStatus status,
        Integer totalCount,
        Integer completedCount,
        Integer failedCount,
        LocalDateTime createTime) {}
