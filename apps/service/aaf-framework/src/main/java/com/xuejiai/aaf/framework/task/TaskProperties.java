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
        /** 是否启动通用队列消费者 */
        private boolean enabled = true;

        /** 消费者线程数 */
        private int consumerThreads = 2;

        /** 拉取超时时间 */
        private Duration pollTimeout = Duration.ofSeconds(5);

        /** pending 消息达到该空闲时间后允许其他消费者接管 */
        private Duration pendingMinIdle = Duration.ofMinutes(5);

        /** pending 恢复扫描间隔 */
        private Duration reclaimInterval = Duration.ofSeconds(30);

        /** 单次恢复的 pending 消息上限 */
        private int reclaimBatchSize = 20;

        /** 普通 Stream 最大保留条数 */
        private long streamMaxLength = 100_000;

        /** 死信 Stream 最大保留条数 */
        private long deadLetterMaxLength = 10_000;

        /** 已完成幂等标记保留时间 */
        private Duration completedRetention = Duration.ofDays(7);

        /** 任务执行中租约时间 */
        private Duration processingLease = Duration.ofMinutes(35);

        /** 延迟队列轮询间隔（毫秒） */
        private long delayPollIntervalMs = 1_000;

        /** 单次转移的到期延迟任务上限 */
        private int delayBatchSize = 100;
    }
}
