package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.BudgetUsage;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.Owner;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.OwnerKind;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.Status;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort.Lease;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** PostgreSQL 委托任务唯一生产适配器。 */
public final class JpaDelegatedTaskAdapter implements DelegatedTaskPort {
    private final DelegatedTaskRepository repository;
    private final TaskBoardRepository taskBoards;
    private final ConversationLeasePort leases;

    public JpaDelegatedTaskAdapter(
            DelegatedTaskRepository repository,
            TaskBoardRepository taskBoards,
            ConversationLeasePort leases) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.taskBoards = Objects.requireNonNull(taskBoards, "taskBoards 不能为空");
        this.leases = Objects.requireNonNull(leases, "leases 不能为空");
    }

    @Override
    @Transactional
    public StoredTask create(DelegatedTask task, AssistantCommand command, TaskBoard board) {
        Objects.requireNonNull(task, "task 不能为空");
        Objects.requireNonNull(command, "command 不能为空");
        Objects.requireNonNull(board, "board 不能为空");
        if (repository
                        .findByTenantIdAndTaskId(task.tenantId().value(), task.taskId().value())
                        .isPresent()
                || taskBoards
                        .findByTenantIdAndTaskId(task.tenantId().value(), task.taskId().value())
                        .isPresent()) {
            throw new IllegalStateException("委托任务已存在: " + task.taskId().value());
        }
        if (!task.tenantId().equals(command.tenantId())
                || !task.taskId().equals(command.taskId())
                || !task.taskId().equals(board.taskId())
                || command.executionContract() == null) {
            throw new IllegalArgumentException("委托任务、命令与 TaskBoard 边界不一致");
        }
        var entity = new DelegatedTaskEntity();
        entity.setCommand(command);
        apply(entity, task);
        var boardEntity = new TaskBoardEntity();
        boardEntity.setTenantId(task.tenantId().value());
        boardEntity.setTaskId(task.taskId().value());
        boardEntity.setBoard(board);
        boardEntity.setFencingToken(0L);
        var saved = repository.saveAndFlush(entity);
        taskBoards.saveAndFlush(boardEntity);
        return stored(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredTask> find(TenantId tenantId, TaskId taskId) {
        return repository
                .findByTenantIdAndTaskId(tenantId.value(), taskId.value())
                .map(this::stored);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DelegatedTask> list(TenantId tenantId, UserId userId) {
        return repository
                .findByTenantIdAndUserIdOrderByUpdatedAtDesc(tenantId.value(), userId.value())
                .stream()
                .map(DelegatedTaskEntity::getTask)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredTask> findPendingByConversation(
            TenantId tenantId, ConversationId conversationId) {
        return repository
                .findByTenantIdAndConversationIdAndStatusOrderByPriorityAscCreatedAtAsc(
                        tenantId.value(), conversationId.value(), Status.PENDING.name())
                .stream()
                .map(this::stored)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredTask> findDispatchable(Instant now, int limit) {
        if (limit < 1 || limit > 100)
            throw new IllegalArgumentException("dispatch limit 必须在 1..100");
        return repository.findDispatchable(now, PageRequest.of(0, limit)).stream()
                .map(this::stored)
                .toList();
    }

    @Override
    @Transactional
    public Optional<StoredTask> claim(TenantId tenantId, TaskId taskId, Lease lease, Instant now) {
        leases.requireCurrent(lease);
        var entity = repository.findForUpdate(tenantId.value(), taskId.value()).orElse(null);
        if (entity == null) return Optional.empty();
        var current = entity.getTask();
        if (current.status() != Status.PENDING
                || current.nextRunAt().isAfter(now)
                || current.owner().kind() == OwnerKind.HUMAN) return Optional.empty();
        requireLeaseBoundary(current, lease);
        if (!current.contract().deadline().isAfter(now)) {
            var paused =
                    copy(
                            current,
                            Status.PAUSED,
                            current.owner(),
                            current.budgetUsage(),
                            current.attempts(),
                            current.consecutiveFailures(),
                            current.nextRunAt(),
                            null,
                            null,
                            current.fencingToken(),
                            merge(current.checkpoint(), "deadlineReached", now.toString()),
                            now,
                            current.sessionId(),
                            current.executionId());
            save(entity, paused);
            return Optional.empty();
        }
        var claimed =
                copy(
                        current,
                        Status.RUNNING,
                        current.owner(),
                        current.budgetUsage(),
                        current.attempts() + 1,
                        current.consecutiveFailures(),
                        current.nextRunAt(),
                        lease.ownerId(),
                        lease.expiresAt(),
                        lease.fencingToken(),
                        current.checkpoint(),
                        now,
                        current.sessionId(),
                        current.executionId());
        apply(entity, claimed);
        entity.setCommand(entity.getCommand().withLease(lease, now));
        return Optional.of(stored(repository.saveAndFlush(entity)));
    }

    @Override
    @Transactional(noRollbackFor = BudgetExceededException.class)
    public DelegatedTask renew(TaskId taskId, Lease lease, Instant now) {
        leases.requireCurrent(lease);
        var entity = requireForUpdate(lease.tenantId(), taskId);
        var current = requireCurrent(entity, lease, null);
        requireWithinDeadline(entity, current, now);
        var renewed =
                copy(
                        current,
                        current.status(),
                        current.owner(),
                        current.budgetUsage(),
                        current.attempts(),
                        current.consecutiveFailures(),
                        current.nextRunAt(),
                        lease.ownerId(),
                        lease.expiresAt(),
                        lease.fencingToken(),
                        current.checkpoint(),
                        now,
                        current.sessionId(),
                        current.executionId());
        return save(entity, renewed);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireAgentExecution(InvocationContext context) {
        Objects.requireNonNull(context, "context 不能为空");
        if (context.controlMode()
                != com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode
                        .DELEGATED) return;
        leases.requireCurrent(context.lease());
        var entity =
                repository
                        .findByTenantIdAndTaskId(
                                context.tenantId().value(), context.taskId().value())
                        .orElseThrow(() -> stale("委托任务不存在"));
        requireCurrent(entity, context.lease(), context.executionId());
    }

    @Override
    @Transactional(readOnly = true)
    public void requireExecution(
            TenantId tenantId, TaskId taskId, ExecutionId executionId, Lease lease) {
        leases.requireCurrent(lease);
        var entity =
                repository
                        .findByTenantIdAndTaskId(tenantId.value(), taskId.value())
                        .orElseThrow(() -> stale("委托任务不存在"));
        requireCurrent(entity, lease, executionId);
    }

    @Override
    @Transactional(noRollbackFor = BudgetExceededException.class)
    public DelegatedTask reserveModelCall(InvocationContext context, Instant at) {
        var entity = locked(context);
        var current = entity.getTask();
        requireWithinDeadline(entity, current, at);
        var usage = current.budgetUsage();
        var next =
                new BudgetUsage(
                        usage.modelCalls() + 1,
                        usage.modelTokens(),
                        usage.toolCalls(),
                        usage.toolUnits(),
                        usage.credits());
        if (next.modelCalls() > current.contract().maxModelCalls()) {
            return pauseAndThrow(entity, current, next, "模型调用次数越界", at);
        }
        return save(entity, withUsage(current, next, at));
    }

    @Override
    @Transactional(noRollbackFor = BudgetExceededException.class)
    public DelegatedTask recordModelUsage(
            InvocationContext context, long tokens, BigDecimal credits, Instant at) {
        if (tokens < 0 || credits == null || credits.signum() < 0) {
            throw new IllegalArgumentException("模型预算增量不能为负数");
        }
        var entity = locked(context);
        var current = entity.getTask();
        requireWithinDeadline(entity, current, at);
        var usage = current.budgetUsage();
        var next =
                new BudgetUsage(
                        usage.modelCalls(),
                        usage.modelTokens() + tokens,
                        usage.toolCalls(),
                        usage.toolUnits(),
                        usage.credits().add(credits));
        var limit = current.contract().budget();
        if (next.modelTokens() > limit.modelTokens()
                || next.credits().compareTo(limit.credits()) > 0) {
            return pauseAndThrow(entity, current, next, "模型或积分预算越界", at);
        }
        return save(entity, withUsage(current, next, at));
    }

    @Override
    @Transactional(noRollbackFor = BudgetExceededException.class)
    public DelegatedTask reserveToolCall(InvocationContext context, String action, Instant at) {
        var entity = locked(context);
        var current = entity.getTask();
        requireWithinDeadline(entity, current, at);
        current.contract().requireAction(action);
        var usage = current.budgetUsage();
        var next =
                new BudgetUsage(
                        usage.modelCalls(),
                        usage.modelTokens(),
                        usage.toolCalls() + 1,
                        usage.toolUnits(),
                        usage.credits());
        if (next.toolCalls() > current.contract().maxToolCalls()) {
            return pauseAndThrow(entity, current, next, "工具调用次数越界", at);
        }
        return save(entity, withUsage(current, next, at));
    }

    @Override
    @Transactional(noRollbackFor = BudgetExceededException.class)
    public DelegatedTask recordToolUsage(
            InvocationContext context, long units, BigDecimal credits, Instant at) {
        if (units < 0 || credits == null || credits.signum() < 0) {
            throw new IllegalArgumentException("工具预算增量不能为负数");
        }
        var entity = locked(context);
        var current = entity.getTask();
        requireWithinDeadline(entity, current, at);
        var usage = current.budgetUsage();
        var next =
                new BudgetUsage(
                        usage.modelCalls(),
                        usage.modelTokens(),
                        usage.toolCalls(),
                        usage.toolUnits() + units,
                        usage.credits().add(credits));
        var limit = current.contract().budget();
        if (next.toolUnits() > limit.toolUnits() || next.credits().compareTo(limit.credits()) > 0) {
            return pauseAndThrow(entity, current, next, "工具或积分预算越界", at);
        }
        return save(entity, withUsage(current, next, at));
    }

    @Override
    @Transactional
    public DelegatedTask checkpoint(
            InvocationContext context,
            Map<String, Object> checkpoint,
            Instant nextRunAt,
            Instant at) {
        var entity = locked(context);
        var current = entity.getTask();
        var changed =
                copy(
                        current,
                        current.status(),
                        current.owner(),
                        current.budgetUsage(),
                        current.attempts(),
                        current.consecutiveFailures(),
                        nextRunAt,
                        current.leaseOwner(),
                        current.leaseUntil(),
                        current.fencingToken(),
                        checkpoint,
                        at,
                        current.sessionId(),
                        current.executionId());
        return save(entity, changed);
    }

    @Override
    @Transactional
    public DelegatedTask pause(
            TenantId tenantId, TaskId taskId, Lease lease, String reason, Instant at) {
        leases.requireCurrent(lease);
        var entity = requireForUpdate(tenantId, taskId);
        var current = entity.getTask();
        requireLeaseBoundary(current, lease);
        var checkpoint = merge(current.checkpoint(), "pauseReason", reason);
        var paused =
                copy(
                        current,
                        Status.PAUSED,
                        current.owner(),
                        current.budgetUsage(),
                        current.attempts(),
                        current.consecutiveFailures(),
                        current.nextRunAt(),
                        null,
                        null,
                        lease.fencingToken(),
                        checkpoint,
                        at,
                        current.sessionId(),
                        current.executionId());
        return save(entity, paused);
    }

    @Override
    @Transactional
    public DelegatedTask awaitAuthorization(InvocationContext context, String reason, Instant at) {
        var entity = locked(context);
        var current = entity.getTask();
        var waiting =
                copy(
                        current,
                        Status.AWAITING_AUTHORIZATION,
                        current.owner(),
                        current.budgetUsage(),
                        current.attempts(),
                        current.consecutiveFailures(),
                        current.nextRunAt(),
                        null,
                        null,
                        current.fencingToken(),
                        merge(current.checkpoint(), "authorizationGap", reason),
                        at,
                        current.sessionId(),
                        current.executionId());
        return save(entity, waiting);
    }

    @Override
    @Transactional
    public DelegatedTask resumeAfterAuthorization(
            TenantId tenantId, UserId userId, TaskId taskId, Lease lease, Instant at) {
        leases.requireCurrent(lease);
        var entity = requireOwned(tenantId, userId, taskId);
        var current = entity.getTask();
        requireLeaseBoundary(current, lease);
        if (current.status() != Status.AWAITING_AUTHORIZATION) {
            throw new IllegalStateException("任务当前不在等待授权状态");
        }
        entity.setCommand(entity.getCommand().asResume(at));
        var resumed =
                copy(
                        current,
                        Status.PENDING,
                        current.owner(),
                        current.budgetUsage(),
                        current.attempts(),
                        current.consecutiveFailures(),
                        at,
                        null,
                        null,
                        lease.fencingToken(),
                        current.checkpoint(),
                        at,
                        current.sessionId(),
                        current.executionId());
        return save(entity, resumed);
    }

    @Override
    @Transactional
    public DelegatedTask complete(
            InvocationContext context, Map<String, Object> result, Instant at) {
        var entity = locked(context);
        var current = entity.getTask();
        var completed =
                copy(
                        current,
                        Status.COMPLETED,
                        current.owner(),
                        current.budgetUsage(),
                        current.attempts(),
                        0,
                        current.nextRunAt(),
                        null,
                        null,
                        current.fencingToken(),
                        result,
                        at,
                        current.sessionId(),
                        current.executionId());
        return save(entity, completed);
    }

    @Override
    @Transactional
    public DelegatedTask fail(InvocationContext context, String failure, Instant at) {
        var entity = locked(context);
        var current = entity.getTask();
        var checkpoint = merge(current.checkpoint(), "lastFailure", failure);
        return save(
                entity,
                copy(
                        current,
                        Status.FAILED,
                        current.owner(),
                        current.budgetUsage(),
                        current.attempts(),
                        current.consecutiveFailures() + 1,
                        current.nextRunAt(),
                        null,
                        null,
                        current.fencingToken(),
                        checkpoint,
                        at,
                        current.sessionId(),
                        current.executionId()));
    }

    @Override
    @Transactional
    public DelegatedTask failOrRetry(
            InvocationContext context, String failure, boolean transientFailure, Instant at) {
        var entity = locked(context);
        var current = entity.getTask();
        var failures = current.consecutiveFailures() + 1;
        var policy = current.contract().retryPolicy();
        var retry =
                current.attempts() < policy.maxAttempts()
                        && (!policy.retryTransientOnly() || transientFailure)
                        && current.contract().deadline().isAfter(at.plus(policy.initialBackoff()));
        var checkpoint = merge(current.checkpoint(), "lastFailure", failure);
        if (!retry) {
            return save(
                    entity,
                    copy(
                            current,
                            Status.FAILED,
                            current.owner(),
                            current.budgetUsage(),
                            current.attempts(),
                            failures,
                            current.nextRunAt(),
                            null,
                            null,
                            current.fencingToken(),
                            checkpoint,
                            at,
                            current.sessionId(),
                            current.executionId()));
        }
        var nextAt = at.plus(policy.initialBackoff().multipliedBy(Math.max(1, current.attempts())));
        var nextExecution = new ExecutionId(randomId());
        var nextSession = new SessionId(randomId());
        var nextRun = new RunId(randomId());
        entity.setCommand(
                entity.getCommand()
                        .newExecution(nextExecution, nextSession, nextRun, context.lease(), at));
        return save(
                entity,
                copy(
                        current,
                        Status.PENDING,
                        new Owner(OwnerKind.ASSISTANT, entity.getCommand().assistantId().value()),
                        current.budgetUsage(),
                        current.attempts(),
                        failures,
                        nextAt,
                        null,
                        null,
                        current.fencingToken(),
                        checkpoint,
                        at,
                        nextSession,
                        nextExecution));
    }

    @Override
    @Transactional
    public DelegatedTask cancel(
            TenantId tenantId,
            UserId userId,
            TaskId taskId,
            String reason,
            Lease lease,
            Instant at) {
        leases.requireCurrent(lease);
        var entity = requireOwned(tenantId, userId, taskId);
        var current = entity.getTask();
        requireLeaseBoundary(current, lease);
        if (current.terminal()) return current;
        return save(
                entity,
                copy(
                        current,
                        Status.CANCELED,
                        new Owner(OwnerKind.HUMAN, userId.value()),
                        current.budgetUsage(),
                        current.attempts(),
                        current.consecutiveFailures(),
                        current.nextRunAt(),
                        null,
                        null,
                        lease.fencingToken(),
                        merge(current.checkpoint(), "cancelReason", reason),
                        at,
                        current.sessionId(),
                        current.executionId()));
    }

    @Override
    @Transactional
    public DelegatedTask takeOver(
            TenantId tenantId,
            UserId userId,
            TaskId taskId,
            String reason,
            Lease lease,
            Instant at) {
        leases.requireCurrent(lease);
        var entity = requireOwned(tenantId, userId, taskId);
        var current = entity.getTask();
        requireLeaseBoundary(current, lease);
        if (!current.contract().takeoverPolicy().humanTakeoverAllowed()) {
            throw new IllegalStateException("委托合同禁止人工接管");
        }
        if (current.contract().takeoverPolicy().reasonRequired()
                && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("人工接管必须说明原因");
        }
        return save(
                entity,
                copy(
                        current,
                        Status.PAUSED,
                        new Owner(OwnerKind.HUMAN, userId.value()),
                        current.budgetUsage(),
                        current.attempts(),
                        current.consecutiveFailures(),
                        current.nextRunAt(),
                        null,
                        null,
                        lease.fencingToken(),
                        merge(current.checkpoint(), "takeoverReason", reason),
                        at,
                        current.sessionId(),
                        current.executionId()));
    }

    @Override
    @Transactional
    public StoredTask handBack(
            TenantId tenantId,
            UserId userId,
            TaskId taskId,
            ExecutionId executionId,
            SessionId sessionId,
            Lease lease,
            Instant at) {
        leases.requireCurrent(lease);
        var entity = requireOwned(tenantId, userId, taskId);
        var current = entity.getTask();
        if (current.owner().kind() != OwnerKind.HUMAN
                || !current.contract().takeoverPolicy().handBackAllowed()) {
            throw new IllegalStateException("任务当前不能交回 Agent");
        }
        requireLeaseBoundary(current, lease);
        var runId = new RunId(randomId());
        entity.setCommand(
                entity.getCommand().newExecution(executionId, sessionId, runId, lease, at));
        var returned =
                copy(
                        current,
                        Status.PENDING,
                        new Owner(OwnerKind.ASSISTANT, entity.getCommand().assistantId().value()),
                        current.budgetUsage(),
                        current.attempts(),
                        current.consecutiveFailures(),
                        at,
                        null,
                        null,
                        lease.fencingToken(),
                        current.checkpoint(),
                        at,
                        sessionId,
                        executionId);
        apply(entity, returned);
        return stored(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public DelegatedTask applyInput(ExecutionInput input, Lease lease) {
        leases.requireCurrent(lease);
        var entity = requireOwned(input.tenantId(), input.userId(), input.taskId());
        var current = entity.getTask();
        requireLeaseBoundary(current, lease);
        if (input.kind() == ExecutionInput.Kind.UNRELATED) return current;
        var checkpoint =
                merge(
                        current.checkpoint(),
                        "input:" + input.inputId(),
                        Map.of(
                                "kind",
                                input.kind().name(),
                                "content",
                                input.content(),
                                "receivedAt",
                                input.receivedAt().toString()));
        if (input.kind() == ExecutionInput.Kind.CANCEL) {
            return save(
                    entity,
                    copy(
                            current,
                            Status.CANCELED,
                            new Owner(OwnerKind.HUMAN, input.userId().value()),
                            current.budgetUsage(),
                            current.attempts(),
                            current.consecutiveFailures(),
                            input.receivedAt(),
                            null,
                            null,
                            lease.fencingToken(),
                            checkpoint,
                            input.receivedAt(),
                            current.sessionId(),
                            current.executionId()));
        }
        var nextExecution = new ExecutionId(randomId());
        var nextSession = new SessionId(randomId());
        var nextRun = new RunId(randomId());
        entity.setCommand(
                entity.getCommand()
                        .newExecution(
                                nextExecution, nextSession, nextRun, lease, input.receivedAt())
                        .withInput(input.content(), lease, input.receivedAt()));
        var nextCheckpoint = merge(checkpoint, "resumeInput", input.content());
        return save(
                entity,
                copy(
                        current,
                        Status.PENDING,
                        new Owner(OwnerKind.ASSISTANT, entity.getCommand().assistantId().value()),
                        current.budgetUsage(),
                        current.attempts(),
                        current.consecutiveFailures(),
                        input.receivedAt(),
                        null,
                        null,
                        lease.fencingToken(),
                        nextCheckpoint,
                        input.receivedAt(),
                        nextSession,
                        nextExecution));
    }

    @Override
    @Transactional
    public int recoverExpired(Instant now) {
        var expired = repository.findExpiredRunning(now);
        expired.forEach(
                entity -> {
                    var current = entity.getTask();
                    var recovered =
                            copy(
                                    current,
                                    Status.PENDING,
                                    current.owner(),
                                    current.budgetUsage(),
                                    current.attempts(),
                                    current.consecutiveFailures(),
                                    now,
                                    null,
                                    null,
                                    current.fencingToken(),
                                    merge(current.checkpoint(), "recoveredAt", now.toString()),
                                    now,
                                    current.sessionId(),
                                    current.executionId());
                    entity.setCommand(entity.getCommand().asResume(now));
                    apply(entity, recovered);
                    repository.save(entity);
                });
        repository.flush();
        return expired.size();
    }

    private DelegatedTaskEntity locked(InvocationContext context) {
        leases.requireCurrent(context.lease());
        var entity = requireForUpdate(context.tenantId(), context.taskId());
        requireCurrent(entity, context.lease(), context.executionId());
        return entity;
    }

    private DelegatedTask requireCurrent(
            DelegatedTaskEntity entity, Lease lease, ExecutionId executionId) {
        var task = entity.getTask();
        requireLeaseBoundary(task, lease);
        if (task.status() != Status.RUNNING
                || task.owner().kind() == OwnerKind.HUMAN
                || task.fencingToken() != lease.fencingToken()
                || !Objects.equals(task.leaseOwner(), lease.ownerId())
                || (executionId != null
                        && !task.executionId().equals(executionId)
                        && !isRunningChild(task.tenantId(), task.taskId(), executionId))) {
            throw stale("旧 owner 或旧 execution 禁止写委托任务");
        }
        return task;
    }

    private boolean isRunningChild(TenantId tenantId, TaskId taskId, ExecutionId executionId) {
        return taskBoards
                .findByTenantIdAndTaskId(tenantId.value(), taskId.value())
                .map(TaskBoardEntity::getBoard)
                .stream()
                .flatMap(board -> board.subTasks().values().stream())
                .anyMatch(
                        subTask ->
                                subTask.status()
                                                == com.xuejiai.aaf.framework.intelligent.assistant
                                                        .model.TaskBoard.Status.RUNNING
                                        && subTask.executionId().equals(executionId));
    }

    private void requireWithinDeadline(
            DelegatedTaskEntity entity, DelegatedTask current, Instant at) {
        if (!current.contract().deadline().isAfter(at)) {
            pauseAndThrow(entity, current, current.budgetUsage(), "委托任务已到 deadline", at);
        }
    }

    private DelegatedTask pauseAndThrow(
            DelegatedTaskEntity entity,
            DelegatedTask current,
            BudgetUsage usage,
            String reason,
            Instant at) {
        var paused =
                copy(
                        current,
                        Status.PAUSED,
                        current.owner(),
                        usage,
                        current.attempts(),
                        current.consecutiveFailures(),
                        current.nextRunAt(),
                        null,
                        null,
                        current.fencingToken(),
                        merge(current.checkpoint(), "budgetPause", reason),
                        at,
                        current.sessionId(),
                        current.executionId());
        save(entity, paused);
        throw new BudgetExceededException(reason);
    }

    private DelegatedTask withUsage(DelegatedTask current, BudgetUsage usage, Instant at) {
        return copy(
                current,
                current.status(),
                current.owner(),
                usage,
                current.attempts(),
                current.consecutiveFailures(),
                current.nextRunAt(),
                current.leaseOwner(),
                current.leaseUntil(),
                current.fencingToken(),
                current.checkpoint(),
                at,
                current.sessionId(),
                current.executionId());
    }

    private DelegatedTaskEntity requireOwned(TenantId tenantId, UserId userId, TaskId taskId) {
        var entity = requireForUpdate(tenantId, taskId);
        if (!entity.getUserId().equals(userId.value())) {
            throw new IllegalArgumentException("委托任务不属于当前用户");
        }
        return entity;
    }

    private DelegatedTaskEntity requireForUpdate(TenantId tenantId, TaskId taskId) {
        return repository
                .findForUpdate(tenantId.value(), taskId.value())
                .orElseThrow(() -> new IllegalArgumentException("委托任务不存在: " + taskId.value()));
    }

    private void requireLeaseBoundary(DelegatedTask task, Lease lease) {
        if (!task.tenantId().equals(lease.tenantId())
                || !task.conversationId().equals(lease.conversationId())) {
            throw stale("conversation lease 与任务边界不一致");
        }
    }

    private DelegatedTask save(DelegatedTaskEntity entity, DelegatedTask task) {
        apply(entity, task);
        return repository.saveAndFlush(entity).getTask();
    }

    private void apply(DelegatedTaskEntity entity, DelegatedTask task) {
        entity.setTenantId(task.tenantId().value());
        entity.setUserId(task.userId().value());
        entity.setTaskId(task.taskId().value());
        entity.setConversationId(task.conversationId().value());
        entity.setSource(task.source().name());
        entity.setPriority(task.priority());
        entity.setExecutionId(task.executionId().value());
        entity.setStatus(task.status().name());
        entity.setOwnerKind(task.owner().kind().name());
        entity.setOwnerId(task.owner().ownerId());
        entity.setNextRunAt(task.nextRunAt());
        entity.setLeaseOwner(task.leaseOwner());
        entity.setLeaseUntil(task.leaseUntil());
        entity.setFencingToken(task.fencingToken());
        entity.setTask(task);
        entity.setCreatedAt(task.createdAt());
        entity.setUpdatedAt(task.updatedAt());
    }

    private StoredTask stored(DelegatedTaskEntity entity) {
        return new StoredTask(entity.getTask(), entity.getCommand());
    }

    private static DelegatedTask copy(
            DelegatedTask source,
            Status status,
            Owner owner,
            BudgetUsage usage,
            int attempts,
            int failures,
            Instant nextRunAt,
            String leaseOwner,
            Instant leaseUntil,
            long fencingToken,
            Map<String, Object> checkpoint,
            Instant updatedAt,
            SessionId sessionId,
            ExecutionId executionId) {
        return new DelegatedTask(
                source.tenantId(),
                source.userId(),
                source.taskId(),
                source.conversationId(),
                sessionId,
                executionId,
                source.parentExecutionId(),
                source.source(),
                source.priority(),
                status,
                owner,
                source.contract(),
                usage,
                attempts,
                failures,
                nextRunAt,
                leaseOwner,
                leaseUntil,
                fencingToken,
                checkpoint,
                source.createdAt(),
                updatedAt);
    }

    private static Map<String, Object> merge(Map<String, Object> source, String key, Object value) {
        var result = new java.util.LinkedHashMap<>(source);
        result.put(key, value == null ? "" : value);
        return Map.copyOf(result);
    }

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static StaleExecutionException stale(String message) {
        return new StaleExecutionException(message);
    }
}
