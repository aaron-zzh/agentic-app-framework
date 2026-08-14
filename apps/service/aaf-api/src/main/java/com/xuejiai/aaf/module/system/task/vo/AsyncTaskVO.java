package com.xuejiai.aaf.module.system.task.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.framework.task.AsyncTask;
import com.xuejiai.aaf.framework.task.AsyncTaskStatus;

/** 异步任务只读查询视图。 */
public record AsyncTaskVO(
        Long id,
        String taskId,
        String taskType,
        AsyncTaskStatus status,
        Short priority,
        Integer attemptCount,
        Integer maxRetries,
        String result,
        String lastError,
        Long ownerId,
        Long orgId,
        Long workspaceId,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public static AsyncTaskVO from(AsyncTask task) {
        return new AsyncTaskVO(
                task.getId(),
                task.getTaskId(),
                task.getTaskType(),
                task.getStatus(),
                task.getPriority(),
                task.getAttemptCount(),
                task.getMaxRetries(),
                task.getResult(),
                task.getLastError(),
                task.getOwnerId(),
                task.getOrgId(),
                task.getWorkspaceId(),
                task.getStartedAt(),
                task.getCompletedAt(),
                task.getCreateTime(),
                task.getUpdateTime());
    }
}
