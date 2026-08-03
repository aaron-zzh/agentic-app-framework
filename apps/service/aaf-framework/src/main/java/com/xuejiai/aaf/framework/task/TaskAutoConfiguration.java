package com.xuejiai.aaf.framework.task;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.xuejiai.aaf.framework.engine.task.agent.AgentTaskRuntime;
import com.xuejiai.aaf.framework.engine.task.agent.RetryScheduler;
import com.xuejiai.aaf.framework.engine.task.agent.SpringTaskSchedulerRetryScheduler;

/** 任务调度自动配置。 */
@Configuration
@EnableConfigurationProperties(TaskProperties.class)
public class TaskAutoConfiguration {

    @Bean
    public TaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setVirtualThreads(true);
        scheduler.setThreadNamePrefix("aaf-task-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }

    @Bean
    @ConditionalOnMissingBean(RetryScheduler.class)
    public RetryScheduler retryScheduler(
            TaskScheduler taskScheduler, ObjectProvider<AgentTaskRuntime> runtimeProvider) {
        return new SpringTaskSchedulerRetryScheduler(taskScheduler, runtimeProvider);
    }
}
