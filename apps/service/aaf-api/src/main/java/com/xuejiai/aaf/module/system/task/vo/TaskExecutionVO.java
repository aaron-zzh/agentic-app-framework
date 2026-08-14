package com.xuejiai.aaf.module.system.task.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.framework.task.TaskExecution;

/** 任务执行审计只读查询视图。 */
public record TaskExecutionVO(
        Long id,
        String taskName,
        String taskType,
        String status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long durationMs,
        String errorMessage,
        Integer retryCount,
        Short priority,
        String triggerType,
        String bizId,
        String context,
        Long orgId,
        Long workspaceId,
        LocalDateTime createTime) {

    public static TaskExecutionVO from(TaskExecution execution) {
        return new TaskExecutionVO(
                execution.getId(),
                execution.getTaskName(),
                execution.getTaskType(),
                execution.getStatus(),
                execution.getStartTime(),
                execution.getEndTime(),
                execution.getDurationMs(),
                execution.getErrorMessage(),
                execution.getRetryCount(),
                execution.getPriority(),
                execution.getTriggerType(),
                execution.getBizId(),
                execution.getContext(),
                execution.getOrgId(),
                execution.getWorkspaceId(),
                execution.getCreateTime());
    }
}
