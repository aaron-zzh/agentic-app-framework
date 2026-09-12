package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.time.Instant;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 一次真实运行或 fresh attempt；DIRECT execution 不属于任何 Task。 */
public record Execution(
        TenantId tenantId,
        UserId userId,
        ConversationId conversationId,
        TaskId taskId,
        String planId,
        Integer planRevision,
        String nodeId,
        ExecutionId executionId,
        SessionId sessionId,
        RunId runId,
        CorrelationId correlationId,
        ExecutionId parentExecutionId,
        ExecutionId predecessorExecutionId,
        Scope scope,
        int attemptNo,
        String stateSlotId,
        Status status,
        PromotionState promotionState,
        long sideEffectEpoch,
        Task.Owner ownerSnapshot,
        int consecutiveFailures,
        Instant createdAt,
        Instant updatedAt) {

    public Execution {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(userId, "userId 不能为空");
        Objects.requireNonNull(conversationId, "conversationId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(runId, "runId 不能为空");
        Objects.requireNonNull(correlationId, "correlationId 不能为空");
        Objects.requireNonNull(scope, "scope 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(promotionState, "promotionState 不能为空");
        Objects.requireNonNull(ownerSnapshot, "ownerSnapshot 不能为空");
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
        if (attemptNo < 1 || consecutiveFailures < 0 || sideEffectEpoch < 0) {
            throw new IllegalArgumentException("attemptNo 必须为正数，失败次数与 sideEffectEpoch 不能为负数");
        }
        if (stateSlotId == null || stateSlotId.isBlank()) {
            throw new IllegalArgumentException("stateSlotId 不能为空白");
        }
        switch (scope) {
            case DIRECT -> {
                if (taskId != null || planId != null || planRevision != null || nodeId != null) {
                    throw new IllegalArgumentException(
                            "DIRECT execution 不得绑定 task/plan/node identity");
                }
            }
            case TASK_ROOT -> {
                if (taskId == null || planId != null || planRevision != null || nodeId != null) {
                    throw new IllegalArgumentException("TASK_ROOT 必须且仅绑定 taskId");
                }
            }
            case TASK_NODE -> {
                if (taskId == null
                        || planId == null
                        || planId.isBlank()
                        || planRevision == null
                        || planRevision < 1
                        || nodeId == null
                        || nodeId.isBlank()) {
                    throw new IllegalArgumentException(
                            "TASK_NODE 必须绑定完整 task/plan/revision/node identity");
                }
            }
        }
        if (predecessorExecutionId != null && predecessorExecutionId.equals(executionId)) {
            throw new IllegalArgumentException("predecessorExecutionId 不能指向自身");
        }
        if (parentExecutionId != null && parentExecutionId.equals(executionId)) {
            throw new IllegalArgumentException("parentExecutionId 不能指向自身");
        }
        if (scope != Scope.DIRECT && promotionState != PromotionState.INELIGIBLE) {
            throw new IllegalArgumentException("非 DIRECT Execution 不得进入 promotion 状态机");
        }
        if ((promotionState == PromotionState.PROMOTED) != (status == Status.PROMOTED)) {
            throw new IllegalArgumentException("PROMOTED status 与 promotionState 必须一致");
        }
        if (promotionState == PromotionState.ELIGIBLE && sideEffectEpoch != 0) {
            throw new IllegalArgumentException(
                    "有 side-effect intent 的 DIRECT Execution 不再可 promotion");
        }
    }

    public boolean terminal() {
        return status == Status.COMPLETED
                || status == Status.FAILED
                || status == Status.CANCELED
                || status == Status.PROMOTED
                || status == Status.SUPERSEDED;
    }

    public Execution withStatus(Status next, Instant at) {
        var nextPromotion = next == Status.PROMOTED ? PromotionState.PROMOTED : promotionState;
        return copy(
                next,
                nextPromotion,
                sideEffectEpoch,
                next == Status.FAILED ? consecutiveFailures + 1 : consecutiveFailures,
                at);
    }

    public Execution beginPromotion(Instant at) {
        if (scope != Scope.DIRECT
                || promotionState != PromotionState.ELIGIBLE
                || sideEffectEpoch != 0
                || terminal()) {
            throw new IllegalStateException("DIRECT Execution 当前不可 promotion");
        }
        return copy(status, PromotionState.PROMOTING, sideEffectEpoch, consecutiveFailures, at);
    }

    public Execution completePromotion(Instant at) {
        if (scope != Scope.DIRECT
                || promotionState != PromotionState.PROMOTING
                || sideEffectEpoch != 0) {
            throw new IllegalStateException("DIRECT Execution 未处于 PROMOTING");
        }
        return copy(
                Status.PROMOTED, PromotionState.PROMOTED, sideEffectEpoch, consecutiveFailures, at);
    }

    public Execution recordSideEffectIntent(Instant at) {
        if (promotionState == PromotionState.PROMOTING
                || promotionState == PromotionState.PROMOTED) {
            throw new IllegalStateException("promotion 已开始，禁止登记 side-effect intent");
        }
        return copy(
                status, PromotionState.INELIGIBLE, sideEffectEpoch + 1, consecutiveFailures, at);
    }

    private Execution copy(
            Status nextStatus,
            PromotionState nextPromotionState,
            long nextSideEffectEpoch,
            int nextFailures,
            Instant at) {
        return new Execution(
                tenantId,
                userId,
                conversationId,
                taskId,
                planId,
                planRevision,
                nodeId,
                executionId,
                sessionId,
                runId,
                correlationId,
                parentExecutionId,
                predecessorExecutionId,
                scope,
                attemptNo,
                stateSlotId,
                nextStatus,
                nextPromotionState,
                nextSideEffectEpoch,
                ownerSnapshot,
                nextFailures,
                createdAt,
                at);
    }

    public enum Scope {
        DIRECT,
        TASK_ROOT,
        TASK_NODE
    }

    public enum PromotionState {
        INELIGIBLE,
        ELIGIBLE,
        PROMOTING,
        PROMOTED
    }

    public enum Status {
        CREATED,
        READY,
        DISPATCHED,
        RUNNING,
        AWAITING_AUTHORIZATION,
        AWAITING_CLARIFICATION,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELED,
        PROMOTED,
        SUPERSEDED
    }
}
