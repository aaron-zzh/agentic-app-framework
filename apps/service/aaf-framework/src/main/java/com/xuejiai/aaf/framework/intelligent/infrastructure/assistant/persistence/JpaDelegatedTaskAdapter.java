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
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.BudgetUsage;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.Owner;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.OwnerKind;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.Status;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskCheckpoint;
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
public class JpaDelegatedTaskAdapter implements DelegatedTaskPort {
    private final DelegatedTaskRepository repository;
    private final TaskBoardRepository taskBoards;
    private final TaskInputRepository inputs;
    private final ConversationLeasePort leases;

    public JpaDelegatedTaskAdapter(
            DelegatedTaskRepository repository,
            TaskBoardRepository taskBoards,
            TaskInputRepository inputs,
            ConversationLeasePort leases) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.taskBoards = Objects.requireNonNull(taskBoards, "taskBoards 不能为空");
        this.inputs = Objects.requireNonNull(inputs, "inputs 不能为空");
        this.leases = Objects.requireNonNull(leases, "leases 不能为空");
    }

    @Override
    @Transactional
    public BufferedInput bufferInput(ExecutionInput input) {
        Objects.requireNonNull(input, "input 不能为空");
        var task = requireOwned(input.tenantId(), input.userId(), input.taskId()).getTask();
        var existing = inputs.findById(input.inputId()).orElse(null);
        if (existing != null) {
            if (!existing.getInput().equals(input)) {
                throw new IllegalStateException("inputId 已绑定不同输入事实: " + input.inputId());
            }
            return new BufferedInput(existing.getInput(), false);
        }
        if (task.terminal()) {
            throw new IllegalStateException("终态任务不再接受输入");
        }
        if ((input.kind() == ExecutionInput.Kind.MODIFY
                        || input.kind() == ExecutionInput.Kind.SUPPLEMENT)
                && task.status() != Status.AWAITING_CLARIFICATION) {
            throw new IllegalStateException("结构化参数只能补充当前待澄清任务");
        }
        var entity = new TaskInputEntity();
        entity.setInputId(input.inputId());
        entity.setTenantId(input.tenantId().value());
        entity.setTaskId(input.taskId().value());
        entity.setReceivedAt(input.receivedAt());
        entity.setInput(input);
        return new BufferedInput(inputs.saveAndFlush(entity).getInput(), true);
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
                            current.checkpoint().withAnnotation("deadlineReached", now.toString()),
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
            InvocationContext context, TaskCheckpoint checkpoint, Instant nextRunAt, Instant at) {
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
        var checkpoint = current.checkpoint().withAnnotation("pauseReason", reason);
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
                        current.checkpoint().withAnnotations(result),
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
        var checkpoint = current.checkpoint().withAnnotation("lastFailure", failure);
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
                        current.checkpoint().withAnnotation("cancelReason", reason),
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
                        current.checkpoint().withAnnotation("takeoverReason", reason),
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
                                    current.checkpoint().withAnnotation("recoveredAt", now.toString()),
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
                        current.checkpoint().withAnnotation("budgetPause", reason),
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

    private DelegatedTaskEntity saveEntity(DelegatedTaskEntity entity, DelegatedTask task) {
        apply(entity, task);
        return repository.saveAndFlush(entity);
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
            TaskCheckpoint checkpoint,
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

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static StaleExecutionException stale(String message) {
        return new StaleExecutionException(message);
    }
}
