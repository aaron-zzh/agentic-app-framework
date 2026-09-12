package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.time.Instant;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 单事务读取的 canonical 运行快照；字段均委托给三类持久事实，不单独持久化。 */
public record TaskSnapshot(Task task, Execution execution, TaskDispatch dispatch) {
    public TaskSnapshot {
        Objects.requireNonNull(task, "task 不能为空");
        Objects.requireNonNull(execution, "execution 不能为空");
        Objects.requireNonNull(dispatch, "dispatch 不能为空");
        if (!task.tenantId().equals(execution.tenantId())
                || !task.tenantId().equals(dispatch.tenantId())
                || !task.taskId().equals(execution.taskId())
                || execution.scope() != Execution.Scope.TASK_ROOT
                || !Objects.equals(task.currentRootExecutionId(), execution.executionId())
                || task.currentRootAttemptNo() != execution.attemptNo()
                || !execution.executionId().equals(dispatch.executionId())) {
            throw new IllegalArgumentException("TaskSnapshot current root 或 attempt 不一致");
        }
    }

    public TenantId tenantId() {
        return task.tenantId();
    }

    public UserId userId() {
        return task.userId();
    }

    public TaskId taskId() {
        return task.taskId();
    }

    public ConversationId conversationId() {
        return task.conversationId();
    }

    public SessionId sessionId() {
        return execution.sessionId();
    }

    public ExecutionId executionId() {
        return execution.executionId();
    }

    public ExecutionId parentExecutionId() {
        return execution.parentExecutionId();
    }

    public Task.Source source() {
        return task.source();
    }

    public int priority() {
        return task.priority();
    }

    public Task.Status status() {
        return task.status();
    }

    public Task.Owner owner() {
        return task.owner();
    }

    public ExecutionContract contract() {
        return task.contract();
    }

    public Task.BudgetUsage budgetUsage() {
        return task.budgetUsage();
    }

    public int attempts() {
        return execution.attemptNo();
    }

    public int consecutiveFailures() {
        return execution.consecutiveFailures();
    }

    public Instant nextRunAt() {
        return dispatch.nextRunAt();
    }

    public String leaseOwner() {
        return dispatch.leaseOwner();
    }

    public Instant leaseUntil() {
        return dispatch.leaseUntil();
    }

    public long generation() {
        return dispatch.generation();
    }

    public long fencingToken() {
        return dispatch.fencingToken();
    }

    public TaskCheckpoint checkpoint() {
        return task.checkpoint();
    }

    public Instant createdAt() {
        return task.createdAt();
    }

    public Instant updatedAt() {
        return task.updatedAt();
    }

    public boolean terminal() {
        return task.terminal();
    }

    public static TaskSnapshot initial(
            com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand command,
            Task.Source source,
            int priority,
            Instant at) {
        var owner = new Task.Owner(Task.OwnerKind.ASSISTANT, command.assistantId().value());
        var execution =
                new Execution(
                        command.tenantId(),
                        command.userId(),
                        command.conversationId(),
                        command.taskId(),
                        null,
                        null,
                        null,
                        command.executionId(),
                        command.sessionId(),
                        command.runId(),
                        command.correlationId(),
                        command.parentExecutionId(),
                        null,
                        Execution.Scope.TASK_ROOT,
                        1,
                        command.executionId().value(),
                        Execution.Status.READY,
                        Execution.PromotionState.INELIGIBLE,
                        0,
                        owner,
                        0,
                        at,
                        at);
        var task =
                new Task(
                        command.tenantId(),
                        command.userId(),
                        command.taskId(),
                        command.conversationId(),
                        null,
                        command.runId(),
                        command.correlationId(),
                        null,
                        null,
                        source,
                        priority,
                        Task.Status.READY,
                        command.controlMode(),
                        owner,
                        command.executionContract(),
                        command.completionCriteria(),
                        Task.BudgetUsage.empty(),
                        null,
                        null,
                        command.executionId(),
                        1,
                        null,
                        TaskCheckpoint.empty(),
                        null,
                        at,
                        at);
        var dispatch =
                new TaskDispatch(
                        "dispatch:" + command.executionId().value(),
                        command.tenantId(),
                        command.executionId(),
                        TaskDispatch.Status.PENDING,
                        at,
                        null,
                        null,
                        1,
                        1,
                        0,
                        null,
                        0,
                        at,
                        at);
        return new TaskSnapshot(task, execution, dispatch);
    }

    public TaskSnapshot withRuntime(
            Task.Status status,
            Task.Owner owner,
            Task.BudgetUsage usage,
            int attempts,
            int failures,
            Instant nextRunAt,
            String leaseOwner,
            Instant leaseUntil,
            long fencingToken,
            TaskCheckpoint checkpoint,
            Instant updatedAt,
            SessionId sessionId,
            ExecutionId executionId) {
        if (!execution.executionId().equals(executionId)
                || !execution.sessionId().equals(sessionId)
                || execution.attemptNo() != attempts) {
            throw new IllegalArgumentException(
                    "withRuntime 仅允许同 attempt 更新；fresh attempt 必须使用 withFreshAttempt");
        }
        var nextTask =
                task.withRuntime(status, owner, usage, checkpoint, task.recoveryPoint(), updatedAt);
        var executionStatus =
                switch (status) {
                    case READY, DRAFT, PLANNING -> Execution.Status.READY;
                    case RUNNING, VERIFYING -> Execution.Status.RUNNING;
                    case AWAITING_AUTHORIZATION -> Execution.Status.AWAITING_AUTHORIZATION;
                    case AWAITING_CLARIFICATION -> Execution.Status.AWAITING_CLARIFICATION;
                    case PAUSED -> Execution.Status.PAUSED;
                    case PAUSING ->
                            throw new IllegalArgumentException(
                                    "PAUSING 必须通过 TaskMaterializationPort 请求");
                    case CANCELING ->
                            throw new IllegalArgumentException(
                                    "CANCELING 必须通过 TaskMaterializationPort 请求");
                    case COMPLETED -> Execution.Status.COMPLETED;
                    case CANCELED -> Execution.Status.CANCELED;
                    case FAILED -> Execution.Status.FAILED;
                };
        var nextExecution =
                new Execution(
                        execution.tenantId(),
                        execution.userId(),
                        execution.conversationId(),
                        execution.taskId(),
                        execution.planId(),
                        execution.planRevision(),
                        execution.nodeId(),
                        executionId,
                        sessionId,
                        execution.runId(),
                        execution.correlationId(),
                        execution.parentExecutionId(),
                        execution.predecessorExecutionId(),
                        execution.scope(),
                        attempts,
                        execution.stateSlotId(),
                        executionStatus,
                        execution.promotionState(),
                        execution.sideEffectEpoch(),
                        owner,
                        failures,
                        execution.createdAt(),
                        updatedAt);
        var dispatchStatus =
                leaseOwner != null
                        ? TaskDispatch.Status.CLAIMED
                        : switch (status) {
                            case READY, DRAFT, PLANNING -> TaskDispatch.Status.PENDING;
                            case PAUSED,
                                    AWAITING_AUTHORIZATION,
                                    AWAITING_CLARIFICATION,
                                    VERIFYING,
                                    CANCELED ->
                                    TaskDispatch.Status.CANCELED;
                            case PAUSING ->
                                    throw new IllegalArgumentException(
                                            "PAUSING 必须通过 TaskMaterializationPort 请求");
                            case CANCELING ->
                                    throw new IllegalArgumentException(
                                            "CANCELING 必须通过 TaskMaterializationPort 请求");
                            case COMPLETED, FAILED -> TaskDispatch.Status.DONE;
                            case RUNNING ->
                                    throw new IllegalArgumentException(
                                            "RUNNING snapshot 必须携带 lease");
                        };
        var generation = dispatch.generation();
        var nextDispatch =
                new TaskDispatch(
                        dispatch.dispatchId(),
                        dispatch.tenantId(),
                        executionId,
                        dispatchStatus,
                        nextRunAt,
                        leaseOwner,
                        leaseUntil,
                        generation,
                        fencingToken,
                        dispatch.deliveryAttempts(),
                        dispatch.lastError(),
                        dispatch.version(),
                        dispatch.createdAt(),
                        updatedAt);
        return new TaskSnapshot(nextTask, nextExecution, nextDispatch);
    }

    public TaskSnapshot withFreshAttempt(
            com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand command,
            Task.Owner owner,
            int consecutiveFailures,
            Instant nextRunAt,
            long fencingToken,
            TaskCheckpoint checkpoint,
            Instant at) {
        if (!task.taskId().equals(command.taskId())) {
            throw new IllegalArgumentException("fresh attempt 与 Task 边界不一致");
        }
        if (execution.executionId().equals(command.executionId())) {
            throw new IllegalArgumentException("fresh attempt 必须使用新 executionId");
        }
        var attemptNo = execution.attemptNo() + 1;
        var nextExecution =
                new Execution(
                        execution.tenantId(),
                        execution.userId(),
                        execution.conversationId(),
                        task.taskId(),
                        execution.planId(),
                        execution.planRevision(),
                        execution.nodeId(),
                        command.executionId(),
                        command.sessionId(),
                        command.runId(),
                        command.correlationId(),
                        execution.parentExecutionId(),
                        execution.executionId(),
                        execution.scope(),
                        attemptNo,
                        command.executionId().value(),
                        Execution.Status.READY,
                        Execution.PromotionState.INELIGIBLE,
                        0,
                        owner,
                        consecutiveFailures,
                        at,
                        at);
        var nextTask =
                task.withRuntime(
                                Task.Status.READY,
                                owner,
                                task.budgetUsage(),
                                checkpoint,
                                task.recoveryPoint(),
                                at)
                        .withCurrentRoot(command.executionId(), attemptNo, at);
        var nextDispatch =
                new TaskDispatch(
                        "dispatch:" + command.executionId().value(),
                        task.tenantId(),
                        command.executionId(),
                        TaskDispatch.Status.PENDING,
                        nextRunAt,
                        null,
                        null,
                        1,
                        Math.max(1, fencingToken),
                        0,
                        null,
                        0,
                        at,
                        at);
        return new TaskSnapshot(nextTask, nextExecution, nextDispatch);
    }
}
