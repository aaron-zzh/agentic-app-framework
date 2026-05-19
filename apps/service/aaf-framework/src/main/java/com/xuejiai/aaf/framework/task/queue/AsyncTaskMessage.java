package com.xuejiai.aaf.framework.task.queue;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 异步任务。
 *
 * @param id 任务 ID
 * @param type 任务类型（路由到对应 handler）
 * @param payload 任务载荷（JSON 字符串）
 * @param priority 优先级 0-9，0 最高
 * @param maxRetries 最大重试次数
 * @param createdAt 创建时间
 */
public record AsyncTaskMessage(
        String id, String type, String payload, int priority, int maxRetries, LocalDateTime createdAt) {

    public AsyncTaskMessage(String type, String payload) {
        this(UUID.randomUUID().toString(), type, payload, 5, 3, LocalDateTime.now());
    }

    public AsyncTaskMessage(String type, String payload, int priority) {
        this(UUID.randomUUID().toString(), type, payload, priority, 3, LocalDateTime.now());
    }
}
