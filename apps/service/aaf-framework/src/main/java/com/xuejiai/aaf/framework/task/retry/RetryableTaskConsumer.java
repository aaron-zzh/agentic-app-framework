package com.xuejiai.aaf.framework.task.retry;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.meta.runtime.ExecutionMeta;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskRuntime;
import com.xuejiai.aaf.framework.task.queue.AsyncTaskMessage;
import com.xuejiai.aaf.framework.task.queue.RedisStreamTaskQueue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 可重试的任务消费包装器。
 *
 * <p>失败后按指数退避重新入队，超过最大重试次数转入死信队列。 执行通过 {@link TaskRuntime} 统一分发，含监控和通知。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetryableTaskConsumer {

    private final RedisStreamTaskQueue taskQueue;
    private final TaskRuntime taskRuntime;

    private final RetryPolicy retryPolicy = RetryPolicy.DEFAULT;

    /**
     * 执行任务，失败时自动重试。
     *
     * @param task 任务消息
     * @param attempt 当前尝试次数（从 1 开始）
     */
    public void executeWithRetry(AsyncTaskMessage task, int attempt) {
        var result =
                taskRuntime.submit(
                        task.type(),
                        task.payload(),
                        ExecutionMeta.queue((short) task.priority(), task.payload()));

        if (result.success()) return;

        // 未找到任务或执行失败
        if (attempt >= task.maxRetries()) {
            log.error("任务 {} 重试 {} 次后仍失败，转入死信队列", task.id(), attempt);
            taskQueue.sendToDeadLetter(task);
        } else {
            var delay = retryPolicy.delayForAttempt(attempt);
            log.warn("任务 {} 第 {} 次失败，{}ms 后重试", task.id(), attempt, delay.toMillis());
            taskQueue.enqueueWithDelay(task, delay);
        }
    }

    /** 从死信队列重新入队（手动重试） */
    public void retryFromDeadLetter(AsyncTaskMessage task) {
        taskQueue.enqueue(
                new AsyncTaskMessage(
                        task.id(),
                        task.type(),
                        task.payload(),
                        task.priority(),
                        task.maxRetries(),
                        task.createdAt()));
        log.info("死信任务重新入队: {} [{}]", task.id(), task.type());
    }
}
