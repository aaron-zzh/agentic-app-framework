package com.xuejiai.aaf.framework.task.queue;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 异步任务。
 *
 * @param id 任务 ID
 * @param type 任务类型（路由到对应 handler）
 * @param payload 任务载荷（JSON 字符串）
 * @param priority 优先级 0-9，0 最高
 * @param maxRetries 最大重试次数（不含首次执行）
 * @param attempt 已执行失败次数，首次执行为 0
 * @param createdAt 创建时间
 * @param lastError 最近一次失败原因
 */
public record AsyncTaskMessage(
        String id,
        String type,
        String payload,
        int priority,
        int maxRetries,
        int attempt,
        LocalDateTime createdAt,
        String lastError) {

    private static final int MAX_ERROR_LENGTH = 2_000;

    public AsyncTaskMessage {
        id = requireText(id, "id");
        type = requireText(type, "type");
        payload = Objects.requireNonNullElse(payload, "");
        if (priority < 0 || priority > 9) {
            throw new IllegalArgumentException("priority 必须在 0-9 之间");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries 不能小于 0");
        }
        if (attempt < 0 || attempt > maxRetries) {
            throw new IllegalArgumentException("attempt 必须在 0-maxRetries 之间");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        lastError = truncate(lastError);
    }

    public AsyncTaskMessage(String type, String payload) {
        this(UUID.randomUUID().toString(), type, payload, 5, 3, 0, LocalDateTime.now(), null);
    }

    public AsyncTaskMessage(String type, String payload, int priority) {
        this(
                UUID.randomUUID().toString(),
                type,
                payload,
                priority,
                3,
                0,
                LocalDateTime.now(),
                null);
    }

    public AsyncTaskMessage nextAttempt(String error) {
        return new AsyncTaskMessage(
                id, type, payload, priority, maxRetries, attempt + 1, createdAt, error);
    }

    public AsyncTaskMessage withLastError(String error) {
        return new AsyncTaskMessage(
                id, type, payload, priority, maxRetries, attempt, createdAt, error);
    }

    public AsyncTaskMessage resetForRetry() {
        return new AsyncTaskMessage(id, type, payload, priority, maxRetries, 0, createdAt, null);
    }

    private static String requireText(String value, String field) {
        var normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return normalized;
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }
}
