package com.xuejiai.aaf.module.ai.assistant.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask;

public record DelegatedTaskVO(
        String taskId,
        String conversationId,
        String executionId,
        String sessionId,
        String status,
        String ownerKind,
        String ownerId,
        long modelCalls,
        long modelTokens,
        long toolCalls,
        long toolUnits,
        BigDecimal credits,
        int attempts,
        int consecutiveFailures,
        Instant nextRunAt,
        Instant leaseUntil,
        Map<String, Object> checkpoint,
        Instant createdAt,
        Instant updatedAt) {

    public static DelegatedTaskVO from(DelegatedTask task) {
        return new DelegatedTaskVO(
                task.taskId().value(),
                task.conversationId().value(),
                task.executionId().value(),
                task.sessionId().value(),
                task.status().name(),
                task.owner().kind().name(),
                task.owner().ownerId(),
                task.budgetUsage().modelCalls(),
                task.budgetUsage().modelTokens(),
                task.budgetUsage().toolCalls(),
                task.budgetUsage().toolUnits(),
                task.budgetUsage().credits(),
                task.attempts(),
                task.consecutiveFailures(),
                task.nextRunAt(),
                task.leaseUntil(),
                task.checkpoint(),
                task.createdAt(),
                task.updatedAt());
    }
}
