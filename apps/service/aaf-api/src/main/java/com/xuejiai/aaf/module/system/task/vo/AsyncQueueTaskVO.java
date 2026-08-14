package com.xuejiai.aaf.module.system.task.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.framework.task.AsyncTask;
import com.xuejiai.aaf.framework.task.AsyncTaskStatus;

/** 通用异步任务查询视图。 */
public record AsyncQueueTaskVO(
        String taskId,
        String taskType,
        AsyncTaskStatus status,
        int attemptCount,
        int maxRetries,
        String result,
        String lastError,
        Long ownerId,
        Long orgId,
        Long workspaceId,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public static AsyncQueueTaskVO from(AsyncTask task) {
        return new AsyncQueueTaskVO(
                task.getTaskId(),
                task.getTaskType(),
                task.getStatus(),
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
