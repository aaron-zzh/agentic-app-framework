package com.xuejiai.aaf.framework.task;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 任务调度自动配置。 */
@Configuration
@EnableConfigurationProperties(TaskProperties.class)
public class TaskAutoConfiguration {

    @Bean
    public TaskScheduler taskScheduler(TaskProperties props) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(props.getScheduler().getPoolSize());
        scheduler.setThreadNamePrefix("aaf-task-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
