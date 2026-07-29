package com.xuejiai.aaf.framework.engine.task.agent;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/** 基于 Spring 调度器的默认智能体任务重试实现。 */
@Component
@ConditionalOnMissingBean(RetryScheduler.class)
public class SpringTaskSchedulerRetryScheduler implements RetryScheduler {

    private final TaskScheduler taskScheduler;
    private final ObjectProvider<AgentTaskRuntime> runtimeProvider;

    public SpringTaskSchedulerRetryScheduler(
            TaskScheduler taskScheduler, ObjectProvider<AgentTaskRuntime> runtimeProvider) {
        this.taskScheduler = taskScheduler;
        this.runtimeProvider = runtimeProvider;
    }

    @Override
    public void scheduleRetry(String taskType, String taskId, String tenantId, Duration backoff) {
        taskScheduler.schedule(
                () -> runtimeProvider.getObject().dispatch(taskType, taskId, tenantId, "RETRY"),
                Instant.now().plus(backoff));
    }
}
