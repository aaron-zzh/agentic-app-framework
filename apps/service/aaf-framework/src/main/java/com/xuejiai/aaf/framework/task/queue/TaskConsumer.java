package com.xuejiai.aaf.framework.task.queue;

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

import com.xuejiai.aaf.framework.engine.meta.runtime.ExecutionMeta;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskRuntime;
import com.xuejiai.aaf.framework.task.TaskProperties;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 任务消费者。使用 XREADGROUP 消费 Redis Stream，按优先级轮询。 消费到消息后通过 {@link TaskRuntime} 统一执行（含监控、超时、通知）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskConsumer {

    private static final String GROUP = "aaf-consumers";
    private static final String CONSUMER_NAME = "consumer-1";
    private static final List<String> STREAMS =
            List.of("task_queue:high", "task_queue:normal", "task_queue:low");

    private final StringRedisTemplate redisTemplate;
    private final TaskRuntime taskRuntime;
    private final TaskProperties taskProperties;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ensureGroups();
        running.set(true);
        var threads = taskProperties.getQueue().getConsumerThreads();
        executor =
                Executors.newFixedThreadPool(
                        threads, r -> Thread.ofVirtual().name("task-consumer").unstarted(r));
        for (int i = 0; i < threads; i++) {
            executor.submit(this::pollLoop);
        }
        log.info("任务消费者启动，线程数: {}", threads);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (executor != null) executor.shutdownNow();
    }

    private void pollLoop() {
        var timeout = taskProperties.getQueue().getPollTimeout();
        while (running.get()) {
            try {
                for (var stream : STREAMS) {
                    var messages =
                            redisTemplate
                                    .opsForStream()
                                    .read(
                                            Consumer.from(GROUP, CONSUMER_NAME),
                                            org.springframework.data.redis.connection.stream
                                                    .StreamReadOptions.empty()
                                                    .count(1)
                                                    .block(timeout),
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

        // 通过元引擎运行时统一执行（含监控、超时控制、通知）
        var priority = (short) (STREAMS.indexOf(stream) * 3); // high=0, normal=3, low=6
        var result =
                taskRuntime.submit(
                        task.type(), task.payload(), ExecutionMeta.queue(priority, task.payload()));
        if (!result.success()) {
            log.error("队列任务执行失败: {} [{}] - {}", task.id(), task.type(), result.error());
        }
        // 无论成功失败均 ACK，重试由 RetryableTaskConsumer 处理
        redisTemplate.opsForStream().acknowledge(stream, GROUP, record.getId());
    }

    private void ensureGroups() {
        for (var stream : STREAMS) {
            try {
                redisTemplate.opsForStream().createGroup(stream, GROUP);
            } catch (Exception ignored) {
                // 组已存在时忽略
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
