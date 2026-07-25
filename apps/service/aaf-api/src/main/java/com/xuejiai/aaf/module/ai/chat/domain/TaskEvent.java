package com.xuejiai.aaf.module.ai.chat.domain;

import java.time.Instant;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;

/** 兼容旧聊天任务 API 的只读事件 DTO，持久化真理源为 ExecutionEvent。 */
public record TaskEvent(
        String eventId,
        long eventOffset,
        String tenantId,
        String taskId,
        String executionId,
        long sequence,
        String subtaskKey,
        String type,
        String payloadJson,
        Instant createTime) {

    public static TaskEvent from(long eventOffset, ExecutionEvent event) {
        var payload = event.payload().values();
        var legacyType = payload.get("legacyType");
        var subtask = payload.get("subtaskKey");
        return new TaskEvent(
                event.eventId().value(),
                eventOffset,
                event.tenantId().value(),
                event.taskId().value(),
                event.executionId().value(),
                event.sequence(),
                subtask == null ? null : subtask.toString(),
                legacyType == null ? event.type().name() : legacyType.toString(),
                JsonUtils.toJsonString(payload),
                event.createdAt());
    }
}
