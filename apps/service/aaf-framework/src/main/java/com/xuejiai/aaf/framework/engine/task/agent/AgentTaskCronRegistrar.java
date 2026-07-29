package com.xuejiai.aaf.framework.engine.task.agent;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** 智能体任务 cron 触发注册器。 */
@Component
@RequiredArgsConstructor
public class AgentTaskCronRegistrar {

    private final TaskScheduler taskScheduler;
    private final AgentTaskRuntime agentTaskRuntime;

    public void register(String taskType, String taskId, String tenantId, String cronExpression) {
        taskScheduler.schedule(
                () -> agentTaskRuntime.dispatch(taskType, taskId, tenantId, "CRON"),
                new CronTrigger(cronExpression));
    }
}
