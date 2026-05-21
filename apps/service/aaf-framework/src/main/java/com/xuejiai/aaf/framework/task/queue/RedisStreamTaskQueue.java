package com.xuejiai.aaf.framework.task.queue;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Redis Stream 的任务队列实现。
 *
 * <p>优先级通过多个 Stream 实现：task_queue:high（0-2）/ task_queue:normal（3-6）/ task_queue:low（7-9）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStreamTaskQueue implements TaskQueue {

    private static final String STREAM_HIGH = "task_queue:high";
    private static final String STREAM_NORMAL = "task_queue:normal";
    private static final String STREAM_LOW = "task_queue:low";
    public static final String STREAM_DEAD = "task_queue:dead";

    private final StringRedisTemplate redisTemplate;
    private final ScheduledExecutorService delayScheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> Thread.ofVirtual().name("task-delay").unstarted(r));

    @Override
    public String enqueue(AsyncTaskMessage task) {
        var stream = resolveStream(task.priority());
        var record = StreamRecords.string(toMap(task)).withStreamKey(stream);
        var recordId = redisTemplate.opsForStream().add(record);
        log.debug("任务入队: {} -> {} [{}]", task.id(), stream, recordId);
        return recordId != null ? recordId.getValue() : null;
    }

    @Override
    public void enqueueWithDelay(AsyncTaskMessage task, Duration delay) {
        delayScheduler.schedule(() -> enqueue(task), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** 将任务转入死信队列 */
    public void sendToDeadLetter(AsyncTaskMessage task) {
        var record = StreamRecords.string(toMap(task)).withStreamKey(STREAM_DEAD);
        redisTemplate.opsForStream().add(record);
        log.warn("任务转入死信队列: {} [{}]", task.id(), task.type());
    }

    private String resolveStream(int priority) {
        if (priority <= 2) return STREAM_HIGH;
        if (priority <= 6) return STREAM_NORMAL;
        return STREAM_LOW;
    }

    private Map<String, String> toMap(AsyncTaskMessage task) {
        return Map.of(
                "id", task.id(),
                "type", task.type(),
                "payload", task.payload(),
                "priority", String.valueOf(task.priority()),
                "maxRetries", String.valueOf(task.maxRetries()),
                "createdAt", task.createdAt().toString());
    }

    /** 从 Map 还原任务消息 */
    public static AsyncTaskMessage fromMap(Map<String, String> map) {
        return new AsyncTaskMessage(
                map.get("id"),
                map.get("type"),
                map.get("payload"),
                Integer.parseInt(map.getOrDefault("priority", "5")),
                Integer.parseInt(map.getOrDefault("maxRetries", "3")),
                java.time.LocalDateTime.parse(map.get("createdAt")));
    }
}
