package com.xuejiai.aaf.framework.task.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.xuejiai.aaf.framework.engine.meta.runtime.ExecutionMeta;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskResult;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskRuntime;
import com.xuejiai.aaf.framework.task.TaskProperties;
import com.xuejiai.aaf.framework.task.queue.AsyncTaskMessage;
import com.xuejiai.aaf.framework.task.queue.RedisStreamTaskQueue;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class RetryableTaskConsumerTest extends BaseMockitoUnitTest {

    @Mock private RedisStreamTaskQueue taskQueue;
    @Mock private TaskRuntime taskRuntime;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RetryableTaskConsumer consumer;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        consumer =
                new RetryableTaskConsumer(
                        taskQueue, taskRuntime, redisTemplate, new TaskProperties());
    }

    @Test
    @DisplayName("Given 任务执行成功 When 消费 Then 写入完成标记且不安排重试")
    void should_mark_completed_when_execution_succeeds() {
        // 准备参数
        var task = task(0, 3);
        when(taskRuntime.submit(eq(task.type()), eq(task.payload()), any(ExecutionMeta.class)))
                .thenReturn(TaskResult.ok());

        // 调用
        var outcome = consumer.executeWithRetry(task);

        // 断言
        assertThat(outcome).isEqualTo(RetryableTaskConsumer.ProcessingOutcome.SUCCESS);
        verify(valueOperations)
                .set(eq("task_queue:completed:" + task.id()), eq("1"), eq(Duration.ofDays(7)));
        verify(taskQueue, never()).enqueueWithDelay(any(), any());
        verify(taskQueue, never()).sendToDeadLetter(any());
    }

    @Test
    @DisplayName("Given 任务首次失败 When 尚可重试 Then 持久化 attempt=1 的延迟任务")
    void should_schedule_next_attempt_when_execution_fails() {
        // 准备参数
        var task = task(0, 3);
        when(taskRuntime.submit(eq(task.type()), eq(task.payload()), any(ExecutionMeta.class)))
                .thenReturn(TaskResult.fail("redis unavailable"));
        var taskCaptor = ArgumentCaptor.forClass(AsyncTaskMessage.class);

        // 调用
        var outcome = consumer.executeWithRetry(task);

        // 断言
        assertThat(outcome).isEqualTo(RetryableTaskConsumer.ProcessingOutcome.RETRY_SCHEDULED);
        verify(taskQueue).enqueueWithDelay(taskCaptor.capture(), eq(Duration.ofSeconds(1)));
        assertThat(taskCaptor.getValue().attempt()).isEqualTo(1);
        assertThat(taskCaptor.getValue().lastError()).isEqualTo("redis unavailable");
        verify(taskQueue, never()).sendToDeadLetter(any());
    }

    @Test
    @DisplayName("Given 已达到最大重试次数 When 再次失败 Then 写入死信且不再调度")
    void should_dead_letter_when_max_retries_reached() {
        // 准备参数
        var task = task(2, 2);
        when(taskRuntime.submit(eq(task.type()), eq(task.payload()), any(ExecutionMeta.class)))
                .thenReturn(TaskResult.fail("permanent failure"));
        var taskCaptor = ArgumentCaptor.forClass(AsyncTaskMessage.class);

        // 调用
        var outcome = consumer.executeWithRetry(task);

        // 断言
        assertThat(outcome).isEqualTo(RetryableTaskConsumer.ProcessingOutcome.DEAD_LETTERED);
        verify(taskQueue).sendToDeadLetter(taskCaptor.capture());
        assertThat(taskCaptor.getValue().lastError()).isEqualTo("permanent failure");
        verify(taskQueue, never()).enqueueWithDelay(any(), any());
    }

    private AsyncTaskMessage task(int attempt, int maxRetries) {
        return new AsyncTaskMessage(
                "task-1",
                "TODO_CLEAR_DONE",
                "{}",
                8,
                maxRetries,
                attempt,
                LocalDateTime.of(2026, 7, 30, 12, 0),
                null);
    }
}
