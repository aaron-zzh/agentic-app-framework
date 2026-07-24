package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.Objects;

/** CompletionValidator 的明确业务决策。 */
public record CompletionDecision(
        Outcome outcome, String reason, AssistantTask.RecoveryPoint recoveryPoint) {

    public CompletionDecision {
        Objects.requireNonNull(outcome, "CompletionDecision outcome 不能为空");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("CompletionDecision reason 不能为空白");
        }
        if (outcome != Outcome.COMPLETED && recoveryPoint == null) {
            throw new IllegalArgumentException("未完成决策必须提供恢复点");
        }
    }

    public enum Outcome {
        COMPLETED,
        CONTINUE_REPAIR,
        NEEDS_USER,
        FAILED,
        HANDOFF
    }
}
