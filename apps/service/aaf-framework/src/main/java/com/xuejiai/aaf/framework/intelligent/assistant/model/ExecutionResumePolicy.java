package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.Objects;

/** 根据冻结身份与状态槽条件确定恢复方式。 */
public final class ExecutionResumePolicy {

    public Decision decide(Request request) {
        Objects.requireNonNull(request, "request 不能为空");
        var execution = Objects.requireNonNull(request.execution(), "execution 不能为空");
        if (execution.terminal()
                || !request.sameOwner()
                || !request.sameProfile()
                || !request.stateSlotAvailable()
                || !request.stateSlotCompatible()) {
            return Decision.FRESH_ATTEMPT;
        }
        return switch (execution.status()) {
            case AWAITING_AUTHORIZATION, AWAITING_CLARIFICATION, PAUSED, DISPATCHED ->
                    Decision.SAME_ATTEMPT;
            default -> Decision.REJECT;
        };
    }

    public record Request(
            Execution execution,
            boolean sameOwner,
            boolean sameProfile,
            boolean stateSlotAvailable,
            boolean stateSlotCompatible) {}

    public enum Decision {
        SAME_ATTEMPT,
        FRESH_ATTEMPT,
        REJECT
    }
}
