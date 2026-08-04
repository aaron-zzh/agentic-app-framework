package com.xuejiai.aaf.module.ai.aigc.task.event;

import java.time.Instant;
import java.util.UUID;

import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;

/** AIGC 媒体子任务终态事件，不携带可变 Entity。 */
public record AigcTaskTerminalEvent(
        UUID eventId,
        Long taskId,
        Long executionRunId,
        String status,
        Long outputMediaVersionId,
        String failureCode,
        Instant occurredAt) {

    public static AigcTaskTerminalEvent from(AigcTask task) {
        return new AigcTaskTerminalEvent(
                UUID.randomUUID(),
                task.getId(),
                null,
                task.getStatus(),
                task.getOutputMediaVersionId(),
                task.getErrorMsg(),
                Instant.now());
    }
}
