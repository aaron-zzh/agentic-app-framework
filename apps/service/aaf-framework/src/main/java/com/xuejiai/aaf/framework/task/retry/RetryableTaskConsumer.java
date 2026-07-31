package com.xuejiai.aaf.framework.task.retry;

import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.meta.runtime.ExecutionMeta;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskExecutionInProgressException;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskRuntime;
import com.xuejiai.aaf.framework.task.TaskProperties;
import com.xuejiai.aaf.framework.task.queue.AsyncTaskMessage;
import com.xuejiai.aaf.framework.task.queue.RedisStreamTaskQueue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 执行队列任务，并在失败时可靠调度重试或写入死信。 */
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
            var result =
                    taskRuntime.submit(
                            task.type(),
                            task.payload(),
                            ExecutionMeta.queue((short) task.priority(), task.payload()));
            if (result.success()) {
                redisTemplate
                        .opsForValue()
                        .set(completedKey, "1", taskProperties.getQueue().getCompletedRetention());
                return ProcessingOutcome.SUCCESS;
            }

            var error = result.error() == null ? "任务执行失败" : result.error();
            if (task.attempt() >= task.maxRetries()) {
                taskQueue.sendToDeadLetter(task.withLastError(error));
                log.error("任务 {} 达到最大重试次数 {}，已转入死信", task.id(), task.maxRetries());
                return ProcessingOutcome.DEAD_LETTERED;
            }

            var retryNumber = task.attempt() + 1;
            var retryTask = task.nextAttempt(error);
            var delay = retryPolicy.delayForAttempt(retryNumber);
            taskQueue.enqueueWithDelay(retryTask, delay);
            log.warn("任务 {} 第 {} 次执行失败，{}ms 后重试", task.id(), retryNumber, delay.toMillis());
            return ProcessingOutcome.RETRY_SCHEDULED;
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
