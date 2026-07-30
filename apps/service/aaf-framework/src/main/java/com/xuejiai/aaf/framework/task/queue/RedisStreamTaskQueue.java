package com.xuejiai.aaf.framework.task.queue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.task.TaskProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 基于 Redis Stream 的优先级任务队列。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStreamTaskQueue implements TaskQueue, DeadLetterQueue {

    private static final String STREAM_HIGH = "task_queue:high";
    private static final String STREAM_NORMAL = "task_queue:normal";
    private static final String STREAM_LOW = "task_queue:low";
    public static final String STREAM_DEAD = "task_queue:dead";
    private static final String DELAY_QUEUE = "task_queue:delayed";
    private static final String BOOTSTRAP_FIELD = "_bootstrap";

    private final StringRedisTemplate redisTemplate;
    private final TaskProperties taskProperties;

    @Override
    public String enqueue(AsyncTaskMessage task) {
        var stream = resolveStream(task.priority());
        var recordId =
                addRecord(stream, toMap(task), taskProperties.getQueue().getStreamMaxLength());
        log.debug("任务入队: {} -> {} [{}]", task.id(), stream, recordId);
        return recordId.getValue();
    }

    @Override
    public void enqueueWithDelay(AsyncTaskMessage task, Duration delay) {
        if (delay == null || delay.isNegative() || delay.isZero()) {
            enqueue(task);
            return;
        }
        var executeAt = System.currentTimeMillis() + delay.toMillis();
        redisTemplate.opsForZSet().add(DELAY_QUEUE, JsonUtils.toJsonString(task), executeAt);
    }

    /** 到期任务先写入 Stream，再删除 ZSet 成员；崩溃窗口最多产生重复消息，不会丢任务。 */
    @Scheduled(fixedDelayString = "${aaf.task.queue.delay-poll-interval-ms:1000}")
    public void dispatchDueTasks() {
        if (!taskProperties.getQueue().isEnabled()) {
            return;
        }
        var dueTasks =
                redisTemplate
                        .opsForZSet()
                        .rangeByScore(
                                DELAY_QUEUE,
                                Double.NEGATIVE_INFINITY,
                                System.currentTimeMillis(),
                                0,
                                taskProperties.getQueue().getDelayBatchSize());
        if (dueTasks == null || dueTasks.isEmpty()) {
            return;
        }
        for (var serialized : dueTasks) {
            try {
                var task = JsonUtils.parseObject(serialized, AsyncTaskMessage.class);
                enqueue(task);
                redisTemplate.opsForZSet().remove(DELAY_QUEUE, serialized);
            } catch (RuntimeException e) {
                log.error("延迟任务转入 Stream 失败，将保留等待下次调度", e);
            }
        }
    }

    /** 只有死信写入成功后调用方才可 ACK 原消息。 */
    public void sendToDeadLetter(AsyncTaskMessage task) {
        var recordId =
                addRecord(
                        STREAM_DEAD,
                        toMap(task),
                        taskProperties.getQueue().getDeadLetterMaxLength());
        log.warn("任务转入死信队列: {} [{}] -> {}", task.id(), task.type(), recordId);
    }

    public void sendMalformedToDeadLetter(Map<String, String> fields, RuntimeException cause) {
        var malformed =
                new AsyncTaskMessage(
                        UUID.randomUUID().toString(),
                        "__MALFORMED__",
                        JsonUtils.toJsonString(fields),
                        9,
                        0,
                        0,
                        LocalDateTime.now(),
                        cause.getMessage());
        sendToDeadLetter(malformed);
    }

    @Override
    public List<DeadLetterMessage> list(int offset, int limit) {
        return redisTemplate
                .opsForStream()
                .reverseRange(
                        STREAM_DEAD, Range.unbounded(), Limit.limit().offset(offset).count(limit))
                .stream()
                .map(
                        record ->
                                new DeadLetterMessage(
                                        record.getId().getValue(), toTask(record.getValue())))
                .toList();
    }

    @Override
    public long count() {
        var size = redisTemplate.opsForStream().size(STREAM_DEAD);
        return size == null ? 0 : size;
    }

    @Override
    public boolean retry(String recordId) {
        var records =
                redisTemplate.opsForStream().range(STREAM_DEAD, Range.closed(recordId, recordId));
        if (records.isEmpty()) {
            return false;
        }
        var task = toTask(records.getFirst().getValue()).resetForRetry();
        enqueue(task);
        redisTemplate.opsForStream().delete(STREAM_DEAD, RecordId.of(recordId));
        log.info("死信任务重新入队: {} [{}]", task.id(), task.type());
        return true;
    }

    void ensureGroup(String stream, String group) {
        RecordId bootstrapId = null;
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(stream))) {
            bootstrapId =
                    addRecord(
                            stream,
                            Map.of(BOOTSTRAP_FIELD, "true"),
                            taskProperties.getQueue().getStreamMaxLength());
        }
        try {
            redisTemplate.opsForStream().createGroup(stream, ReadOffset.from("0-0"), group);
        } catch (RuntimeException e) {
            if (!containsMessage(e, "BUSYGROUP")) {
                throw e;
            }
        } finally {
            if (bootstrapId != null) {
                redisTemplate.opsForStream().delete(stream, bootstrapId);
            }
        }
    }

    private RecordId addRecord(String stream, Map<String, String> fields, long maxLength) {
        var record = StreamRecords.string(fields).withStreamKey(stream);
        var options = XAddOptions.maxlen(maxLength).approximateTrimming(true);
        var recordId = redisTemplate.opsForStream().add(record, options);
        if (recordId == null) {
            throw new IllegalStateException("Redis Stream 写入未返回消息 ID: " + stream);
        }
        return recordId;
    }

    private String resolveStream(int priority) {
        if (priority <= 2) return STREAM_HIGH;
        if (priority <= 6) return STREAM_NORMAL;
        return STREAM_LOW;
    }

    private Map<String, String> toMap(AsyncTaskMessage task) {
        var fields = new HashMap<String, String>();
        fields.put("id", task.id());
        fields.put("type", task.type());
        fields.put("payload", task.payload());
        fields.put("priority", String.valueOf(task.priority()));
        fields.put("maxRetries", String.valueOf(task.maxRetries()));
        fields.put("attempt", String.valueOf(task.attempt()));
        fields.put("createdAt", task.createdAt().toString());
        if (task.lastError() != null) {
            fields.put("lastError", task.lastError());
        }
        return fields;
    }

    private AsyncTaskMessage toTask(Map<Object, Object> values) {
        var fields = new HashMap<String, String>();
        values.forEach((key, value) -> fields.put(String.valueOf(key), String.valueOf(value)));
        return fromMap(fields);
    }

    public static AsyncTaskMessage fromMap(Map<String, String> map) {
        return new AsyncTaskMessage(
                map.get("id"),
                map.get("type"),
                map.get("payload"),
                Integer.parseInt(map.get("priority")),
                Integer.parseInt(map.get("maxRetries")),
                Integer.parseInt(map.get("attempt")),
                LocalDateTime.parse(map.get("createdAt")),
                map.get("lastError"));
    }

    private boolean containsMessage(Throwable throwable, String text) {
        for (var current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(text)) {
                return true;
            }
        }
        return false;
    }
}
