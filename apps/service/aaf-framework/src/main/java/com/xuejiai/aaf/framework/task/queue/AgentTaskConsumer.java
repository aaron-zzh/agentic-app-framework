package com.xuejiai.aaf.framework.task.queue;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.task.agent.AgentTaskRuntime;
import com.xuejiai.aaf.framework.task.TaskProperties;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 智能体任务 Redis Stream 消费者，与通用 TaskRuntime 消费路径相互独立。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTaskConsumer {

    private static final String GROUP = "aaf-agent-task-consumers";
    private static final List<String> STREAMS =
            List.of(
                    "agent_task_queue:high",
                    "agent_task_queue:normal",
                    "agent_task_queue:low");

    private final StringRedisTemplate redisTemplate;
    private final AgentTaskRuntime agentTaskRuntime;
    private final TaskProperties taskProperties;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final String consumerPrefix = "agent-task-consumer-" + UUID.randomUUID();
    private ExecutorService executor;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ensureGroups();
        running.set(true);
        var threads = taskProperties.getQueue().getConsumerThreads();
        var threadSequence = new AtomicInteger();
        executor =
                Executors.newFixedThreadPool(
                        threads,
                        runnable ->
                                Thread.ofVirtual()
                                        .name("agent-task-consumer-" + threadSequence.incrementAndGet())
                                        .unstarted(runnable));
        for (int index = 0; index < threads; index++) {
            var consumerIndex = index;
            executor.submit(() -> pollLoop(consumerIndex));
        }
        log.info("智能体任务消费者启动，线程数: {}", threads);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("智能体任务消费者未能在 5s 内正常退出");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private void pollLoop(int consumerIndex) {
        var timeout = taskProperties.getQueue().getPollTimeout();
        var consecutiveErrors = new AtomicInteger();
        var consumerName = consumerPrefix + "-" + consumerIndex;
        while (running.get()) {
            try {
                for (var stream : STREAMS) {
                    var messages =
                            redisTemplate
                                    .opsForStream()
                                    .read(
                                            Consumer.from(GROUP, consumerName),
                                            StreamReadOptions.empty().count(1).block(timeout),
                                            StreamOffset.create(
                                                    stream, ReadOffset.lastConsumed()));
                    if (messages != null) {
                        for (var message : messages) {
                            processMessage(stream, message);
                        }
                    }
                }
                consecutiveErrors.set(0);
            } catch (org.springframework.dao.QueryTimeoutException e) {
                log.debug("智能体任务 Stream 阻塞读超时，继续轮询");
            } catch (org.springframework.data.redis.RedisConnectionFailureException e) {
                var errors = consecutiveErrors.incrementAndGet();
                var backoffMs = Math.min(1000L * errors, 30_000L);
                log.warn(
                        "智能体任务 Redis 连接异常（第 {} 次），{}ms 后重试: {}",
                        errors,
                        backoffMs,
                        e.getMessage());
                sleep(backoffMs);
            } catch (Exception e) {
                if (running.get()) {
                    var errors = consecutiveErrors.incrementAndGet();
                    var backoffMs = Math.min(500L * errors, 10_000L);
                    log.error("智能体任务消费循环异常（第 {} 次），{}ms 后重试", errors, backoffMs, e);
                    sleep(backoffMs);
                }
            }
        }
    }

    private void processMessage(String stream, MapRecord<String, Object, Object> record) {
        var fields = new HashMap<String, String>();
        record.getValue()
                .forEach((key, value) -> fields.put(String.valueOf(key), String.valueOf(value)));

        agentTaskRuntime.dispatch(
                requireField(fields, "taskType"),
                requireField(fields, "taskId"),
                requireField(fields, "tenantId"),
                requireField(fields, "triggerType"));
        redisTemplate.opsForStream().acknowledge(stream, GROUP, record.getId());
    }

    private String requireField(HashMap<String, String> fields, String name) {
        var value = fields.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("智能体任务消息缺少字段: " + name);
        }
        return value;
    }

    private void ensureGroups() {
        for (var stream : STREAMS) {
            try {
                redisTemplate.opsForStream().createGroup(stream, GROUP);
            } catch (Exception ignored) {
                // 消费组已存在时忽略
            }
        }
    }

    private void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
