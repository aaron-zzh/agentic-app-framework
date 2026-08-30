package com.xuejiai.aaf.module.ai.assistant.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask;

public record DelegatedTaskVO(
        String taskId,
        String title,
        String description,
        String conversationId,
        String executionId,
        String sessionId,
        String source,
        int priority,
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

    public static DelegatedTaskVO from(DelegatedTask task, String goalDescription) {
        var titleDescription = splitGoalDescription(goalDescription);
        return new DelegatedTaskVO(
                task.taskId().value(),
                titleDescription.title(),
                titleDescription.description(),
                task.conversationId().value(),
                task.executionId().value(),
                task.sessionId().value(),
                task.source().name(),
                task.priority(),
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
                task.checkpoint().annotations(),
                task.createdAt(),
                task.updatedAt());
    }

    private static TitleDescription splitGoalDescription(String raw) {
        if (raw == null) {
            return new TitleDescription(null, null);
        }
        var separator = "\n\n---\n\n";
        var separatorIndex = raw.indexOf(separator);
        if (separatorIndex < 0) {
            return new TitleDescription(raw, null);
        }
        return new TitleDescription(
                raw.substring(0, separatorIndex),
                raw.substring(separatorIndex + separator.length()));
    }

    private record TitleDescription(String title, String description) {}
}
