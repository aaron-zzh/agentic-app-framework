package com.xuejiai.aaf.framework.task.queue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.task.TaskMonitor;
import com.xuejiai.aaf.framework.task.TaskProperties;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务消费者。使用 XREADGROUP 消费 Redis Stream，按优先级轮询。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskConsumer {

    private static final String GROUP = "aaf-consumers";
    private static final String CONSUMER_NAME = "consumer-1";
    private static final List<String> STREAMS = List.of("task_queue:high", "task_queue:normal", "task_queue:low");

    private final StringRedisTemplate redisTemplate;
    private final TaskHandlerRegistry handlerRegistry;
    private final TaskMonitor taskMonitor;
    private final TaskProperties taskProperties;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ensureGroups();
        running.set(true);
        var threads = taskProperties.getQueue().getConsumerThreads();
        executor = Executors.newFixedThreadPool(threads, r -> Thread.ofVirtual().name("task-consumer").unstarted(r));
        for (int i = 0; i < threads; i++) {
            executor.submit(this::pollLoop);
        }
        log.info("任务消费者启动，线程数: {}", threads);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void pollLoop() {
        var timeout = taskProperties.getQueue().getPollTimeout();
        while (running.get()) {
            try {
                for (var stream : STREAMS) {
                    var messages = redisTemplate.opsForStream().read(
                            Consumer.from(GROUP, CONSUMER_NAME),
                            org.springframework.data.redis.connection.stream.StreamReadOptions.empty()
                                    .count(1).block(timeout),
                            StreamOffset.create(stream, ReadOffset.lastConsumed()));
                    if (messages != null) {
                        for (var msg : messages) {
                            processMessage(stream, msg);
                        }
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.error("消费循环异常", e);
                    sleep(1000);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void processMessage(String stream, MapRecord<String, Object, Object> record) {
        var map = new java.util.HashMap<String, String>();
        record.getValue().forEach((k, v) -> map.put(String.valueOf(k), String.valueOf(v)));
        var task = RedisStreamTaskQueue.fromMap(map);
        var handler = handlerRegistry.getHandler(task.type());
        if (handler == null) {
            log.warn("未找到任务处理器: {}", task.type());
            redisTemplate.opsForStream().acknowledge(stream, GROUP, record.getId());
            return;
        }
        var executionId = taskMonitor.recordStart(task.type(), "async");
        try {
            handler.handle(task);
            taskMonitor.recordSuccess(executionId);
            redisTemplate.opsForStream().acknowledge(stream, GROUP, record.getId());
        } catch (Exception e) {
            log.error("任务处理失败: {} [{}]", task.id(), task.type(), e);
            taskMonitor.recordFailure(executionId, e.getMessage());
            // 重试由 RetryableTaskConsumer 处理，此处仍 ACK 避免重复消费
            redisTemplate.opsForStream().acknowledge(stream, GROUP, record.getId());
            throw e;
        }
    }

    private void ensureGroups() {
        for (var stream : STREAMS) {
            try {
                redisTemplate.opsForStream().createGroup(stream, GROUP);
            } catch (Exception e) {
                // 组已存在或 stream 不存在时忽略
            }
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
