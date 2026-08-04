package com.xuejiai.aaf.framework.task.queue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.task.TaskProperties;
import com.xuejiai.aaf.framework.task.retry.RetryableTaskConsumer;

import io.lettuce.core.XAutoClaimArgs;
import io.lettuce.core.api.async.RedisStreamAsyncCommands;
import io.lettuce.core.models.stream.ClaimedMessages;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Redis Stream 通用任务消费者。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskConsumer {

    private static final String GROUP = "aaf-consumers";
    private static final String STREAM_HIGH = "task_queue:high";
    private static final String STREAM_NORMAL = "task_queue:normal";
    private static final String STREAM_LOW = "task_queue:low";
    private static final List<String> STREAMS = List.of(STREAM_HIGH, STREAM_NORMAL, STREAM_LOW);

    private final StringRedisTemplate redisTemplate;
    private final RedisStreamTaskQueue taskQueue;
    private final RetryableTaskConsumer retryableTaskConsumer;
    private final TaskProperties taskProperties;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong lastReclaimAt = new AtomicLong(0);
    private final ConcurrentMap<String, String> reclaimCursors = new ConcurrentHashMap<>();
    private final String consumerPrefix = "task-consumer-" + UUID.randomUUID();
    private ExecutorService executor;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!taskProperties.getQueue().isEnabled()) {
            log.info("通用任务消费者已禁用");
            return;
        }
        tryEnsureGroups();
        running.set(true);
        var threads = taskProperties.getQueue().getConsumerThreads();
        var threadSequence = new AtomicInteger();
        executor =
                Executors.newFixedThreadPool(
                        threads,
                        runnable ->
                                Thread.ofVirtual()
                                        .name("task-consumer-" + threadSequence.incrementAndGet())
                                        .unstarted(runnable));
        for (int index = 0; index < threads; index++) {
            var consumerIndex = index;
            executor.submit(() -> pollLoop(consumerIndex));
        }
        log.info("任务消费者启动，线程数: {}", threads);
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
                log.warn("任务消费者未能在 5s 内正常退出");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void pollLoop(int consumerIndex) {
        var consumerName = consumerPrefix + "-" + consumerIndex;
        var consecutiveErrors = new AtomicInteger();
        while (running.get()) {
            try {
                recoverPendingIfDue(consumerName);
                if (!readAvailable(consumerName)) {
                    readBlocking(consumerName);
                }
                consecutiveErrors.set(0);
            } catch (org.springframework.dao.QueryTimeoutException e) {
                log.debug("Stream 阻塞读超时（无消息），继续轮询");
            } catch (org.springframework.data.redis.RedisConnectionFailureException e) {
                var errors = consecutiveErrors.incrementAndGet();
                var backoffMs = Math.min(1000L * errors, 30_000L);
                log.warn("Redis 连接异常（第 {} 次），{}ms 后重试: {}", errors, backoffMs, e.getMessage());
                sleep(backoffMs);
            } catch (RuntimeException e) {
                if (running.get()) {
                    tryEnsureGroups();
                    var errors = consecutiveErrors.incrementAndGet();
                    var backoffMs = Math.min(500L * errors, 10_000L);
                    log.error("消费循环异常（第 {} 次），{}ms 后重试", errors, backoffMs, e);
                    sleep(backoffMs);
                }
            }
        }
    }

    private boolean readAvailable(String consumerName) {
        for (var stream : STREAMS) {
            var messages =
                    redisTemplate
                            .opsForStream()
                            .read(
                                    Consumer.from(GROUP, consumerName),
                                    StreamReadOptions.empty().count(1),
                                    StreamOffset.create(stream, ReadOffset.lastConsumed()));
            if (messages != null && !messages.isEmpty()) {
                processMessage(stream, messages.getFirst());
                return true;
            }
        }
        return false;
    }

    private void readBlocking(String consumerName) {
        var messages =
                redisTemplate
                        .opsForStream()
                        .read(
                                Consumer.from(GROUP, consumerName),
                                StreamReadOptions.empty()
                                        .count(1)
                                        .block(taskProperties.getQueue().getPollTimeout()),
                                StreamOffset.create(STREAM_HIGH, ReadOffset.lastConsumed()),
                                StreamOffset.create(STREAM_NORMAL, ReadOffset.lastConsumed()),
                                StreamOffset.create(STREAM_LOW, ReadOffset.lastConsumed()));
        if (messages == null) {
            return;
        }
        for (var message : messages) {
            processMessage(String.valueOf(message.getStream()), message);
        }
    }

    private void recoverPendingIfDue(String consumerName) {
        var now = System.currentTimeMillis();
        var previous = lastReclaimAt.get();
        var interval = taskProperties.getQueue().getReclaimInterval().toMillis();
        if (now - previous < interval || !lastReclaimAt.compareAndSet(previous, now)) {
            return;
        }
        for (var stream : STREAMS) {
            for (var message : autoClaim(stream, consumerName)) {
                processMessage(stream, message);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<MapRecord<String, Object, Object>> autoClaim(String stream, String consumerName) {
        var streamKey = stream.getBytes(StandardCharsets.UTF_8);
        var group = GROUP.getBytes(StandardCharsets.UTF_8);
        var consumer = consumerName.getBytes(StandardCharsets.UTF_8);
        var cursor = reclaimCursors.getOrDefault(stream, "0-0");
        var claimed =
                redisTemplate.execute(
                        (RedisCallback<ClaimedMessages<byte[], byte[]>>)
                                connection -> {
                                    var commands =
                                            (RedisStreamAsyncCommands<byte[], byte[]>)
                                                    connection.getNativeConnection();
                                    return commands.xautoclaim(
                                                    streamKey,
                                                    new XAutoClaimArgs<byte[]>()
                                                            .consumer(
                                                                    io.lettuce.core.Consumer.from(
                                                                            group, consumer))
                                                            .minIdleTime(
                                                                    taskProperties
                                                                            .getQueue()
                                                                            .getPendingMinIdle())
                                                            .startId(cursor)
                                                            .count(
                                                                    taskProperties
                                                                            .getQueue()
                                                                            .getReclaimBatchSize()))
                                            .toCompletableFuture()
                                            .join();
                                });
        reclaimCursors.put(stream, claimed.getId());
        return claimed.getMessages().stream()
                .map(
                        message -> {
                            var fields = new HashMap<Object, Object>();
                            message.getBody()
                                    .forEach(
                                            (key, value) ->
                                                    fields.put(
                                                            new String(key, StandardCharsets.UTF_8),
                                                            new String(
                                                                    value,
                                                                    StandardCharsets.UTF_8)));
                            return MapRecord.<String, Object, Object>create(stream, fields)
                                    .withId(RecordId.of(message.getId()));
                        })
                .toList();
    }

    private void processMessage(String stream, MapRecord<String, Object, Object> record) {
        var fields = new HashMap<String, String>();
        record.getValue()
                .forEach((key, value) -> fields.put(String.valueOf(key), String.valueOf(value)));
        final AsyncTaskMessage task;
        try {
            task = RedisStreamTaskQueue.fromMap(fields);
        } catch (RuntimeException e) {
            taskQueue.sendMalformedToDeadLetter(fields, e);
            acknowledge(stream, record);
            return;
        }

        // M49：handler 的关系库副作用与 sys_task_inbox 在同一事务提交；Redis completed/processing
        // 仅作为快速去重缓存与并发租约。外部系统副作用仍由 handler 使用稳定 task.id() 保证幂等。
        retryableTaskConsumer.executeWithRetry(task);
        acknowledge(stream, record);
    }

    private void acknowledge(String stream, MapRecord<String, Object, Object> record) {
        redisTemplate.opsForStream().acknowledge(stream, GROUP, record.getId());
    }

    private void tryEnsureGroups() {
        try {
            STREAMS.forEach(stream -> taskQueue.ensureGroup(stream, GROUP));
        } catch (RuntimeException e) {
            log.warn("任务消费组初始化失败，将在消费循环重试: {}", e.getMessage());
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
