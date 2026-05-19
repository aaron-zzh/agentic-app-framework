package com.xuejiai.aaf.framework.task;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/** 任务调度配置属性。 */
@Data
@ConfigurationProperties(prefix = "aaf.task")
public class TaskProperties {

    private Scheduler scheduler = new Scheduler();
    private Queue queue = new Queue();

    @Data
    public static class Scheduler {
        /** 调度线程池大小 */
        private int poolSize = 4;
    }

    @Data
    public static class Queue {
        /** 消费者线程数 */
        private int consumerThreads = 2;
        /** 拉取超时时间 */
        private Duration pollTimeout = Duration.ofSeconds(5);
    }
}
