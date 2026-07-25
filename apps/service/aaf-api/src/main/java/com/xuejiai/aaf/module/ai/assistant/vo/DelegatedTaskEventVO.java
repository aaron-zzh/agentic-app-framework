package com.xuejiai.aaf.module.ai.assistant.vo;

import java.time.Instant;
import java.util.Map;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort.StoredExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId;

/** 委托任务执行事件。 */
public record DelegatedTaskEventVO(
        long eventOffset,
        String eventId,
        String tenantId,
        String conversationId,
        String sessionId,
        String taskId,
        String executionId,
        String runId,
        String parentExecutionId,
        long sequence,
        String type,
        String status,
        String controlMode,
        String ownerType,
        String assistantId,
        String agentId,
        String userId,
        String correlationId,
        String causationId,
        String idempotencyKey,
        Map<String, Object> payload,
        Instant createdAt) {

    public static DelegatedTaskEventVO from(StoredExecutionEvent stored) {
        var event = stored.event();
        return new DelegatedTaskEventVO(
                stored.eventOffset(),
                event.eventId().value(),
                event.tenantId().value(),
                event.conversationId().value(),
                event.sessionId().value(),
                event.taskId().value(),
                event.executionId().value(),
                event.runId().value(),
                value(event.parentExecutionId()),
                event.sequence(),
                event.type().name(),
                event.status().name(),
                event.controlMode().name(),
                event.ownerType().name(),
                value(event.assistantId()),
                value(event.agentId()),
                value(event.userId()),
                event.correlationId().value(),
                value(event.causationId()),
                value(event.idempotencyKey()),
                event.payload().values(),
                event.createdAt());
    }

    private static String value(StableId id) {
        return id == null ? null : id.value();
    }
}
