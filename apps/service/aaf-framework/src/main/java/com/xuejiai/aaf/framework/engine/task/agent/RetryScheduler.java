package com.xuejiai.aaf.framework.engine.task.agent;

import java.time.Duration;

/** 智能体任务失败后的重试调度端口。 */
public interface RetryScheduler {

    void scheduleRetry(String taskType, String taskId, String tenantId, Duration backoff);
}
