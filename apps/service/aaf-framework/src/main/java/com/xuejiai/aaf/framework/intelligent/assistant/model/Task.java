package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 持久目标的唯一根事实；调度租约和单次 attempt 状态分别属于 TaskDispatch 与 Execution。 */
public record Task(
        TenantId tenantId,
        UserId userId,
        TaskId taskId,
        ConversationId conversationId,
        ExecutionId originExecutionId,
        RunId originRunId,
        CorrelationId originCorrelationId,
        String originInputRef,
        String publicContextRef,
        Source source,
        int priority,
        Status status,
        ControlMode controlMode,
        Owner owner,
        ExecutionContract contract,
        CompletionCriteria completionCriteria,
        BudgetUsage budgetUsage,
        String currentPlanId,
        Integer currentPlanRevision,
        ExecutionId currentRootExecutionId,
        int currentRootAttemptNo,
        RootResult rootResult,
        TaskCheckpoint checkpoint,
        RecoveryPoint recoveryPoint,
        Instant createdAt,
        Instant updatedAt) {

    private static final Map<Status, Set<Status>> ALLOWED_TRANSITIONS = buildTransitions();

    public Task {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(userId, "userId 不能为空");
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(conversationId, "conversationId 不能为空");
        source = source == null ? Source.AUTOMATION : source;
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(controlMode, "controlMode 不能为空");
        Objects.requireNonNull(owner, "owner 不能为空");
        Objects.requireNonNull(contract, "contract 不能为空");
        Objects.requireNonNull(completionCriteria, "completionCriteria 不能为空");
        Objects.requireNonNull(budgetUsage, "budgetUsage 不能为空");
        checkpoint = checkpoint == null ? TaskCheckpoint.empty() : checkpoint;
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
        if (priority < 0 || currentRootAttemptNo < 0) {
            throw new IllegalArgumentException("priority 和 currentRootAttemptNo 不能为负数");
        }
        if ((currentPlanId == null) != (currentPlanRevision == null)) {
            throw new IllegalArgumentException("currentPlanId 与 currentPlanRevision 必须同时存在");
        }
        if (currentPlanRevision != null && currentPlanRevision < 1) {
            throw new IllegalArgumentException("currentPlanRevision 必须为正数");
        }
        if ((currentRootExecutionId == null) != (currentRootAttemptNo == 0)) {
            throw new IllegalArgumentException("currentRootExecutionId 与 attemptNo 必须同时存在");
        }
        if (currentPlanId != null && currentRootExecutionId != null) {
            throw new IllegalArgumentException("有 frozen plan 的 Task 不得同时持有 TASK_ROOT");
        }
        if (rootResult != null) {
            if (status != Status.COMPLETED
                    || currentPlanId == null
                    || !currentPlanId.equals(rootResult.planId())
                    || !currentPlanRevision.equals(rootResult.planRevision())) {
                throw new IllegalArgumentException("RootResult 必须绑定已完成 Task 的 current plan");
            }
            if (owner.kind() != OwnerKind.ASSISTANT
                    || !owner.ownerId().equals(rootResult.ownerAssistantId())) {
                throw new IllegalArgumentException("RootResult 必须由 Task Owner Assistant 提交");
            }
        } else if (currentPlanId != null && status == Status.COMPLETED) {
            throw new IllegalArgumentException("planned Task 完成时必须存在 RootResult");
        }
        if (originExecutionId != null && originExecutionId.equals(currentRootExecutionId)) {
            throw new IllegalArgumentException(
                    "promotion 后 Task execution 不能复用 origin DIRECT execution");
        }
        if (owner.kind() == OwnerKind.HUMAN && status == Status.RUNNING) {
            throw new IllegalArgumentException("人工接管期间 Task 不能保持 RUNNING");
        }
    }

    public boolean terminal() {
        return status == Status.COMPLETED || status == Status.CANCELED || status == Status.FAILED;
    }

    public Task transitionTo(
            Status next,
            String reason,
            Actor initiatedBy,
            Owner nextOwner,
            RecoveryPoint nextRecoveryPoint,
            Instant at) {
        Objects.requireNonNull(next, "next status 不能为空");
        requireReason(reason);
        Objects.requireNonNull(initiatedBy, "initiatedBy 不能为空");
        Objects.requireNonNull(nextOwner, "nextOwner 不能为空");
        Objects.requireNonNull(at, "transition at 不能为空");
        if (next == status) return this;
        if (!ALLOWED_TRANSITIONS.getOrDefault(status, Set.of()).contains(next)) {
            throw new IllegalStateException("非法 Task 状态转换: " + status + " -> " + next);
        }
        return copy(
                next,
                controlMode,
                nextOwner,
                budgetUsage,
                currentRootExecutionId,
                currentRootAttemptNo,
                checkpoint.withAnnotation("lastTransitionReason", reason),
                nextRecoveryPoint,
                at);
    }

    public Task changeControlMode(ControlMode next, String reason, Actor initiatedBy, Instant at) {
        Objects.requireNonNull(next, "next controlMode 不能为空");
        requireReason(reason);
        Objects.requireNonNull(initiatedBy, "initiatedBy 不能为空");
        Objects.requireNonNull(at, "transition at 不能为空");
        if (next == controlMode) return this;
        return copy(
                status,
                next,
                owner,
                budgetUsage,
                currentRootExecutionId,
                currentRootAttemptNo,
                checkpoint.withAnnotation("controlModeReason", reason),
                recoveryPoint,
                at);
    }

    public Task withRuntime(
            Status nextStatus,
            Owner nextOwner,
            BudgetUsage nextUsage,
            TaskCheckpoint nextCheckpoint,
            RecoveryPoint nextRecoveryPoint,
            Instant at) {
        return copy(
                nextStatus,
                controlMode,
                nextOwner,
                nextUsage,
                currentRootExecutionId,
                currentRootAttemptNo,
                nextCheckpoint,
                nextRecoveryPoint,
                at);
    }

    public Task withCurrentRoot(ExecutionId executionId, int attemptNo, Instant at) {
        return copy(
                status,
                controlMode,
                owner,
                budgetUsage,
                executionId,
                attemptNo,
                checkpoint,
                recoveryPoint,
                at);
    }

    public Task withCurrentPlan(String planId, int revision, Instant at) {
        if (planId == null || planId.isBlank() || revision < 1) {
            throw new IllegalArgumentException("current plan identity 非法");
        }
        return new Task(
                tenantId,
                userId,
                taskId,
                conversationId,
                originExecutionId,
                originRunId,
                originCorrelationId,
                originInputRef,
                publicContextRef,
                source,
                priority,
                status,
                controlMode,
                owner,
                contract,
                completionCriteria,
                budgetUsage,
                planId,
                revision,
                null,
                0,
                rootResult,
                checkpoint,
                recoveryPoint,
                createdAt,
                at);
    }

    public Task completeWithRootResult(RootResult result, Instant at) {
        Objects.requireNonNull(result, "rootResult 不能为空");
        Objects.requireNonNull(at, "complete at 不能为空");
        if (rootResult != null) {
            throw new IllegalStateException("Task RootResult 已提交");
        }
        if (status != Status.VERIFYING) {
            throw new IllegalStateException("只有 VERIFYING Task 可以提交 RootResult");
        }
        if (!Objects.equals(currentPlanId, result.planId())
                || !Objects.equals(currentPlanRevision, result.planRevision())) {
            throw new IllegalStateException("RootResult 不属于 Task current plan");
        }
        if (owner.kind() != OwnerKind.ASSISTANT
                || !owner.ownerId().equals(result.ownerAssistantId())) {
            throw new IllegalStateException("RootResult 提交者不是 Task Owner Assistant");
        }
        return new Task(
                tenantId,
                userId,
                taskId,
                conversationId,
                originExecutionId,
                originRunId,
                originCorrelationId,
                originInputRef,
                publicContextRef,
                source,
                priority,
                Status.COMPLETED,
                controlMode,
                owner,
                contract,
                completionCriteria,
                budgetUsage,
                currentPlanId,
                currentPlanRevision,
                null,
                0,
                result,
                checkpoint,
                recoveryPoint,
                createdAt,
                at);
    }

    private Task copy(
            Status nextStatus,
            ControlMode nextControlMode,
            Owner nextOwner,
            BudgetUsage nextUsage,
            ExecutionId nextRootExecutionId,
            int nextRootAttemptNo,
            TaskCheckpoint nextCheckpoint,
            RecoveryPoint nextRecoveryPoint,
            Instant at) {
        return new Task(
                tenantId,
                userId,
                taskId,
                conversationId,
                originExecutionId,
                originRunId,
                originCorrelationId,
                originInputRef,
                publicContextRef,
                source,
                priority,
                nextStatus,
                nextControlMode,
                nextOwner,
                contract,
                completionCriteria,
                nextUsage,
                currentPlanId,
                currentPlanRevision,
                nextRootExecutionId,
                nextRootAttemptNo,
                rootResult,
                nextCheckpoint,
                nextRecoveryPoint,
                createdAt,
                at);
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("状态转换理由不能为空白");
        }
        return reason;
    }

    private static Map<Status, Set<Status>> buildTransitions() {
        var allowed = new EnumMap<Status, Set<Status>>(Status.class);
        allowed.put(
                Status.DRAFT,
                Set.of(
                        Status.PLANNING,
                        Status.READY,
                        Status.PAUSING,
                        Status.CANCELING,
                        Status.FAILED));
        allowed.put(
                Status.PLANNING,
                Set.of(
                        Status.READY,
                        Status.RUNNING,
                        Status.AWAITING_AUTHORIZATION,
                        Status.AWAITING_CLARIFICATION,
                        Status.PAUSED,
                        Status.PAUSING,
                        Status.CANCELING,
                        Status.FAILED));
        allowed.put(
                Status.READY,
                Set.of(
                        Status.PLANNING,
                        Status.RUNNING,
                        Status.PAUSED,
                        Status.PAUSING,
                        Status.CANCELING,
                        Status.FAILED));
        allowed.put(
                Status.RUNNING,
                Set.of(
                        Status.VERIFYING,
                        Status.READY,
                        Status.AWAITING_AUTHORIZATION,
                        Status.AWAITING_CLARIFICATION,
                        Status.PAUSED,
                        Status.PAUSING,
                        Status.COMPLETED,
                        Status.CANCELING,
                        Status.FAILED));
        allowed.put(
                Status.VERIFYING,
                Set.of(
                        Status.RUNNING,
                        Status.COMPLETED,
                        Status.AWAITING_CLARIFICATION,
                        Status.PAUSED,
                        Status.PAUSING,
                        Status.CANCELING,
                        Status.FAILED));
        allowed.put(
                Status.AWAITING_AUTHORIZATION,
                Set.of(
                        Status.READY,
                        Status.RUNNING,
                        Status.AWAITING_CLARIFICATION,
                        Status.PAUSED,
                        Status.PAUSING,
                        Status.CANCELING,
                        Status.FAILED));
        allowed.put(
                Status.AWAITING_CLARIFICATION,
                Set.of(
                        Status.PLANNING,
                        Status.READY,
                        Status.RUNNING,
                        Status.PAUSED,
                        Status.PAUSING,
                        Status.CANCELING,
                        Status.FAILED));
        allowed.put(Status.PAUSING, Set.of(Status.PAUSED, Status.CANCELING));
        allowed.put(
                Status.PAUSED,
                Set.of(
                        Status.PLANNING,
                        Status.READY,
                        Status.RUNNING,
                        Status.PAUSING,
                        Status.CANCELING,
                        Status.FAILED));
        allowed.put(Status.CANCELING, Set.of(Status.CANCELED));
        allowed.put(Status.COMPLETED, Set.of());
        allowed.put(Status.CANCELED, Set.of());
        allowed.put(Status.FAILED, Set.of(Status.PLANNING, Status.READY));
        return Map.copyOf(allowed);
    }

    public record RootResult(
            String result,
            String completionEvidence,
            String ownerAssistantId,
            String planId,
            int planRevision,
            String sourceNodeId,
            ExecutionId executionId,
            int attemptNo,
            String dispatchId,
            long generation,
            long fencingToken,
            Instant committedAt) {
        public RootResult {
            result = Objects.requireNonNull(result, "root result 不能为空");
            completionEvidence =
                    Objects.requireNonNull(completionEvidence, "completionEvidence 不能为空");
            ownerAssistantId = requireText(ownerAssistantId, "ownerAssistantId");
            planId = requireText(planId, "planId");
            sourceNodeId = requireText(sourceNodeId, "sourceNodeId");
            if (!"aggregator".equals(sourceNodeId)) {
                throw new IllegalArgumentException("RootResult sourceNodeId 必须是 aggregator");
            }
            Objects.requireNonNull(executionId, "executionId 不能为空");
            dispatchId = requireText(dispatchId, "dispatchId");
            Objects.requireNonNull(committedAt, "committedAt 不能为空");
            if (planRevision < 1 || attemptNo < 1 || generation < 1 || fencingToken < 1) {
                throw new IllegalArgumentException(
                        "RootResult revision/attempt/generation/fence 必须为正数");
            }
        }

        private static String requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " 不能为空白");
            }
            return value.trim();
        }
    }

    public record BudgetUsage(
            long modelCalls, long modelTokens, long toolCalls, long toolUnits, BigDecimal credits) {
        public BudgetUsage {
            Objects.requireNonNull(credits, "credits 不能为空");
            if (modelCalls < 0
                    || modelTokens < 0
                    || toolCalls < 0
                    || toolUnits < 0
                    || credits.signum() < 0) {
                throw new IllegalArgumentException("预算用量不能为负数");
            }
        }

        public static BudgetUsage empty() {
            return new BudgetUsage(0, 0, 0, 0, BigDecimal.ZERO);
        }
    }

    public record Owner(OwnerKind kind, String ownerId) {
        public Owner {
            Objects.requireNonNull(kind, "owner kind 不能为空");
            if (ownerId == null || ownerId.isBlank())
                throw new IllegalArgumentException("ownerId 不能为空白");
        }
    }

    public record Actor(OwnerKind kind, String actorId) {
        public Actor {
            Objects.requireNonNull(kind, "actor kind 不能为空");
            if (actorId == null || actorId.isBlank())
                throw new IllegalArgumentException("actorId 不能为空白");
        }
    }

    public record RecoveryPoint(String key, String description) {
        public RecoveryPoint {
            if (key == null || key.isBlank())
                throw new IllegalArgumentException("recovery key 不能为空白");
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("recovery description 不能为空白");
            }
        }
    }

    public enum OwnerKind {
        SYSTEM,
        ASSISTANT,
        AGENT,
        HUMAN
    }

    public enum Source {
        CONVERSATION,
        MANUAL,
        AUTOMATION,
        PROMOTION
    }

    public enum Status {
        DRAFT,
        PLANNING,
        READY,
        RUNNING,
        VERIFYING,
        AWAITING_AUTHORIZATION,
        AWAITING_CLARIFICATION,
        PAUSING,
        PAUSED,
        CANCELING,
        COMPLETED,
        CANCELED,
        FAILED
    }
}
