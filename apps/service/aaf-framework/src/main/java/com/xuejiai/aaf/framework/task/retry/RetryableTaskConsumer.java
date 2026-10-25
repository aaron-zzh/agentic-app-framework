package com.xuejiai.aaf.framework.task.retry;

import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.meta.runtime.ExecutionMeta;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskExecutionInProgressException;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskRuntime;
import com.xuejiai.aaf.framework.task.AsyncQueueTaskService;
import com.xuejiai.aaf.framework.task.TaskProperties;
import com.xuejiai.aaf.framework.task.queue.AsyncTaskMessage;
import com.xuejiai.aaf.framework.task.queue.RedisStreamTaskQueue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 执行队列任务，并在失败时可靠调度重试或写入死信。
 *
 * <p>M49 消费保证（**至少一次投递 + 持久 inbox 租约消费**）：
 *
 * <ul>
 *   <li>{@code sys_task_inbox} 持久化 PENDING/PROCESSING/COMPLETED 与租约，防止并发重复处理
 *   <li>{@code task_queue:completed:<id>} 是快速去重缓存，重投时可直接判 DUPLICATE
 *   <li>{@code task_queue:processing:<id>} 租约防止两个消费者同时处理同一任务
 * </ul>
 *
 * <p>handler 在 inbox 事务外执行；崩溃或下游失败后可由租约重领。外部系统副作用仍须使用稳定 {@code task.id()} 或独立持久检查点保证幂等。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetryableTaskConsumer {

    private static final String PROCESSING_PREFIX = "task_queue:processing:";
    private static final String COMPLETED_PREFIX = "task_queue:completed:";
    private static final DefaultRedisScript<Long> RELEASE_LEASE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then "
                            + "return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class);

    private final RedisStreamTaskQueue taskQueue;
    private final TaskRuntime taskRuntime;
    private final AsyncQueueTaskService asyncTaskService;
    private final StringRedisTemplate redisTemplate;
    private final TaskProperties taskProperties;

    private final RetryPolicy retryPolicy = RetryPolicy.DEFAULT;

    /** 执行任务。返回即表示当前消息已经成功、已确认重复、已可靠安排重试或已写入死信，可由调用方 ACK。 */
    public ProcessingOutcome executeWithRetry(AsyncTaskMessage task) {
        var completedKey = COMPLETED_PREFIX + task.id();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(completedKey))) {
            return ProcessingOutcome.DUPLICATE;
        }

        var processingKey = PROCESSING_PREFIX + task.id();
        var leaseToken = UUID.randomUUID().toString();
        var acquired =
                redisTemplate
                        .opsForValue()
                        .setIfAbsent(
                                processingKey,
                                leaseToken,
                                taskProperties.getQueue().getProcessingLease());
        if (!Boolean.TRUE.equals(acquired)) {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(completedKey))) {
                return ProcessingOutcome.DUPLICATE;
            }
            throw new TaskExecutionInProgressException("任务正在由其他消费者处理: " + task.id());
        }

        try {
            asyncTaskService.markRunning(task);
            var result =
                    taskRuntime.submit(
                            task.type(),
                            task.payload(),
                            ExecutionMeta.queue(
                                    (short) task.priority(), task.id(), task.payload()));
            if (result.success()) {
                asyncTaskService.markSucceeded(task.id(), result.output());
                redisTemplate
                        .opsForValue()
                        .set(completedKey, "1", taskProperties.getQueue().getCompletedRetention());
                return ProcessingOutcome.SUCCESS;
            }

            var error = result.error() == null ? "任务执行失败" : result.error();
            if (task.attempt() >= task.maxRetries()) {
                var failedTask = task.withLastError(error);
                taskQueue.sendToDeadLetter(failedTask);
                asyncTaskService.markFailed(failedTask);
                log.error("任务 {} 达到最大重试次数 {}，已转入死信", task.id(), task.maxRetries());
                return ProcessingOutcome.DEAD_LETTERED;
            }

            var retryNumber = task.attempt() + 1;
            var retryTask = task.nextAttempt(error);
            asyncTaskService.markRetryWaiting(retryTask);
            var delay = retryPolicy.delayForAttempt(retryNumber);
            taskQueue.enqueueWithDelay(retryTask, delay);
            log.warn("任务 {} 第 {} 次执行失败，{}ms 后重试", task.id(), retryNumber, delay.toMillis());
            return ProcessingOutcome.RETRY_SCHEDULED;
        } catch (com.xuejiai.aaf.framework.engine.meta.runtime.NonRetryableTaskException failure) {
            var failedTask = task.withLastError(failure.getMessage());
            taskQueue.sendToDeadLetter(failedTask);
            asyncTaskService.markFailed(failedTask);
            log.error("任务 {} 不可重试，已转入死信", task.id());
            return ProcessingOutcome.DEAD_LETTERED;
        } finally {
            redisTemplate.execute(RELEASE_LEASE_SCRIPT, List.of(processingKey), leaseToken);
        }
    }

    public enum ProcessingOutcome {
        SUCCESS,
        DUPLICATE,
        RETRY_SCHEDULED,
        DEAD_LETTERED
    }
}
