package com.xuejiai.aaf.module.company.ops.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.module.company.ops.domain.OpsTaskExecution;

/** 运营任务执行出参。 */
public record OpsTaskExecutionVO(
        Long id,
        Long taskId,
        String status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String result,
        String errorMessage,
        String triggeredBy,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public static OpsTaskExecutionVO from(OpsTaskExecution entity) {
        return new OpsTaskExecutionVO(
                entity.getId(),
                entity.getTaskId(),
                entity.getStatus(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getResult(),
                entity.getErrorMessage(),
                entity.getTriggeredBy(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }
}
