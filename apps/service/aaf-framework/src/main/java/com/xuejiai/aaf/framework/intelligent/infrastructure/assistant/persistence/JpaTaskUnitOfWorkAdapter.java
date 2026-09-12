package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Execution;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task.BudgetUsage;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task.Owner;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task.OwnerKind;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task.Status;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskCheckpoint;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDispatch;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskSnapshot;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort.Lease;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskUnitOfWork;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** canonical Task/Execution/TaskDispatch 唯一写适配器。 */
public class JpaTaskUnitOfWorkAdapter implements TaskUnitOfWork {
    private final TaskRootRepository tasks;
    private final TaskExecutionRepository executions;
    private final TaskDispatchRepository dispatches;
    private final TaskInputRepository inputs;
    private final ConversationLeasePort leases;

    public JpaTaskUnitOfWorkAdapter(
            TaskRootRepository tasks,
            TaskExecutionRepository executions,
            TaskDispatchRepository dispatches,
            TaskInputRepository inputs,
            ConversationLeasePort leases) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
        this.executions = Objects.requireNonNull(executions, "executions 不能为空");
        this.dispatches = Objects.requireNonNull(dispatches, "dispatches 不能为空");
        this.inputs = Objects.requireNonNull(inputs, "inputs 不能为空");
        this.leases = Objects.requireNonNull(leases, "leases 不能为空");
    }

    @Override
    @Transactional
    public StoredTask create(TaskSnapshot snapshot, AssistantCommand command) {
        Objects.requireNonNull(snapshot, "snapshot 不能为空");
        Objects.requireNonNull(command, "command 不能为空");
        if (tasks.findByTenantIdAndTaskId(snapshot.tenantId().value(), snapshot.taskId().value())
                .isPresent()) {
            throw new IllegalStateException("Task 已存在: " + snapshot.taskId().value());
        }
        if (snapshot.task().originExecutionId() != null
                && tasks.findByTenantIdAndOriginExecutionId(
                                snapshot.tenantId().value(),
                                snapshot.task().originExecutionId().value())
                        .isPresent()) {
            throw new IllegalStateException("originExecutionId 已完成 promotion");
        }
        var taskEntity = new TaskRootEntity();
        apply(taskEntity, snapshot.task());
        var executionEntity = new TaskExecutionEntity();
        apply(executionEntity, snapshot.execution(), command);
        var dispatchEntity = new TaskDispatchEntity();
        var requestedDispatch = snapshot.dispatch();
        var initialDispatch =
                new TaskDispatch(
                        requestedDispatch.dispatchId(),
                        requestedDispatch.tenantId(),
                        requestedDispatch.executionId(),
                        TaskDispatch.Status.PENDING,
                        requestedDispatch.nextRunAt(),
                        null,
                        null,
                        1,
                        dispatches.nextFence(),
                        0,
                        requestedDispatch.lastError(),
                        0,
                        requestedDispatch.createdAt(),
                        requestedDispatch.updatedAt());
        apply(dispatchEntity, initialDispatch);
        tasks.save(taskEntity);
        executions.save(executionEntity);
        dispatches.save(dispatchEntity);
        return new StoredTask(
                new TaskSnapshot(snapshot.task(), snapshot.execution(), initialDispatch), command);
    }

    @Override
    @Transactional
    public TaskSnapshot save(TaskSnapshot snapshot, AssistantCommand command) {
        Objects.requireNonNull(snapshot, "snapshot 不能为空");
        Objects.requireNonNull(command, "command 不能为空");
        var taskEntity = requireTaskForUpdate(snapshot.tenantId(), snapshot.taskId());
        var previousExecutionId = taskEntity.getCurrentRootExecutionId();
        if (previousExecutionId != null
                && !previousExecutionId.equals(snapshot.executionId().value())) {
            supersedePrevious(snapshot, previousExecutionId);
        }
        var executionEntity =
                executions
                        .findForUpdate(snapshot.tenantId().value(), snapshot.executionId().value())
                        .orElseGet(TaskExecutionEntity::new);
        var dispatchEntity =
                dispatches
                        .findActiveForUpdate(
                                snapshot.tenantId().value(), snapshot.executionId().value())
                        .orElseGet(TaskDispatchEntity::new);
        apply(taskEntity, snapshot.task());
        apply(executionEntity, snapshot.execution(), command);
        apply(dispatchEntity, snapshot.dispatch());
        tasks.save(taskEntity);
        executions.save(executionEntity);
        dispatches.saveAndFlush(dispatchEntity);
        return snapshot;
    }

    @Override
    @Transactional
    public Task saveRoot(Task task, Lease lease) {
        Objects.requireNonNull(task, "task 不能为空");
        if (task.status() == Status.PAUSING || task.status() == Status.CANCELING) {
            throw new IllegalArgumentException("PAUSING/CANCELING 必须通过 TaskMaterializationPort 请求");
        }
        var entity = requireTaskForUpdate(task.tenantId(), task.taskId());
        if (lease != null) {
            leases.requireCurrent(lease);
            requireLeaseBoundary(entity.getTask(), lease);
        }
        apply(entity, task);
        if (task.currentRootExecutionId() != null
                && (task.status() == Status.PAUSED
                        || task.status() == Status.COMPLETED
                        || task.status() == Status.CANCELED
                        || task.status() == Status.FAILED)) {
            var bundle = requireBundle(entity);
            var executionStatus =
                    switch (task.status()) {
                        case PAUSED -> Execution.Status.PAUSED;
                        case COMPLETED -> Execution.Status.COMPLETED;
                        case CANCELED -> Execution.Status.CANCELED;
                        case FAILED -> Execution.Status.FAILED;
                        default -> throw new IllegalStateException("未支持的终态同步");
                    };
            var dispatchStatus =
                    switch (task.status()) {
                        case PAUSED -> TaskDispatch.Status.CANCELED;
                        case COMPLETED -> TaskDispatch.Status.DONE;
                        case CANCELED -> TaskDispatch.Status.CANCELED;
                        case FAILED -> TaskDispatch.Status.DONE;
                        default -> throw new IllegalStateException("未支持的终态同步");
                    };
            apply(
                    bundle.execution(),
                    withExecutionStatus(
                            bundle.execution().getExecution(), executionStatus, task.updatedAt()),
                    bundle.execution().getCommand());
            apply(
                    bundle.dispatch(),
                    terminalDispatch(
                            bundle.dispatch().getDispatch(), dispatchStatus, task.updatedAt()));
            flush(bundle, entity);
            return entity.getTask();
        }
        return tasks.saveAndFlush(entity).getTask();
    }

    @Override
    @Transactional
    public Execution createExecution(Execution execution, AssistantCommand command) {
        Objects.requireNonNull(execution, "execution 不能为空");
        if (executions
                .findByTenantIdAndExecutionId(
                        execution.tenantId().value(), execution.executionId().value())
                .isPresent()) {
            throw new IllegalStateException("Execution 已存在: " + execution.executionId().value());
        }
        if (execution.scope() == Execution.Scope.DIRECT && execution.taskId() != null) {
            throw new IllegalArgumentException("DIRECT Execution 不得绑定 Task");
        }
        var entity = new TaskExecutionEntity();
        apply(entity, execution, command);
        return executions.saveAndFlush(entity).getExecution();
    }

    @Override
    @Transactional
    public Execution startNodeExecution(
            InvocationContext parentContext,
            Execution execution,
            AssistantCommand command,
            Instant at) {
        Objects.requireNonNull(parentContext, "parentContext 不能为空");
        Objects.requireNonNull(execution, "execution 不能为空");
        Objects.requireNonNull(command, "command 不能为空");
        Objects.requireNonNull(at, "at 不能为空");
        var locked = requireLocked(parentContext);
        requireNodeIdentity(parentContext, execution, command);
        var entity =
                executions
                        .findForUpdate(
                                execution.tenantId().value(), execution.executionId().value())
                        .orElse(null);
        if (entity == null) {
            var created = withExecution(execution, Execution.Status.RUNNING, at);
            var createdEntity = new TaskExecutionEntity();
            apply(createdEntity, created, command);
            var nodeDispatch =
                    new TaskDispatch(
                            "dispatch:" + randomId(),
                            created.tenantId(),
                            created.executionId(),
                            TaskDispatch.Status.CLAIMED,
                            at,
                            parentContext.lease().ownerId(),
                            parentContext.lease().expiresAt(),
                            1,
                            parentContext.lease().fencingToken(),
                            1,
                            null,
                            0,
                            at,
                            at);
            var dispatchEntity = new TaskDispatchEntity();
            apply(dispatchEntity, nodeDispatch);
            executions.save(createdEntity);
            dispatches.saveAndFlush(dispatchEntity);
            return createdEntity.getExecution();
        }
        var current = entity.getExecution();
        requireSameNodeAttempt(current, execution);
        if (current.terminal()) {
            throw stale("终态 TaskNode 不能重新启动");
        }
        var resumed = withExecution(current, Execution.Status.RUNNING, at);
        apply(entity, resumed, command);
        return executions.saveAndFlush(entity).getExecution();
    }

    @Override
    @Transactional
    public Execution updateNodeExecutionStatus(
            InvocationContext parentContext,
            ExecutionId executionId,
            Execution.Status status,
            Instant at) {
        Objects.requireNonNull(parentContext, "parentContext 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(at, "at 不能为空");
        var locked = requireLocked(parentContext);
        var entity =
                executions
                        .findForUpdate(parentContext.tenantId().value(), executionId.value())
                        .orElseThrow(() -> stale("TaskNode Execution 不存在"));
        var current = entity.getExecution();
        if (!Objects.equals(current.taskId(), parentContext.taskId())
                || current.scope() != Execution.Scope.TASK_NODE
                || !Objects.equals(current.parentExecutionId(), parentContext.executionId())
                || current.status() != Execution.Status.RUNNING) {
            throw stale("parent 不匹配或非运行 TaskNode 禁止写入");
        }
        var changed = withExecutionStatus(current, status, at);
        apply(entity, changed, entity.getCommand());
        return executions.saveAndFlush(entity).getExecution();
    }

    @Override
    @Transactional
    public Execution updateExecution(
            TenantId tenantId, ExecutionId executionId, Execution.Status status, Instant at) {
        var entity =
                executions
                        .findForUpdate(tenantId.value(), executionId.value())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Execution 不存在: " + executionId.value()));
        var changed = withExecutionStatus(entity.getExecution(), status, at);
        apply(entity, changed, entity.getCommand());
        return executions.saveAndFlush(entity).getExecution();
    }

    @Override
    @Transactional
    public BufferedInput bufferInput(ExecutionInput input) {
        Objects.requireNonNull(input, "input 不能为空");
        var task = requireOwnedEntity(input.tenantId(), input.userId(), input.taskId()).getTask();
        var existing =
                inputs.findByTenantIdAndInputId(input.tenantId().value(), input.inputId())
                        .orElse(null);
        if (existing != null) {
            if (!existing.getInput().equals(input)) {
                throw new IllegalStateException("inputId 已绑定不同输入事实: " + input.inputId());
            }
            return new BufferedInput(existing.getInput(), false);
        }

        if (task.terminal()
                || task.status() == Status.PAUSING
                || task.status() == Status.CANCELING) {
            throw new IllegalStateException("暂停中、取消中或终态 Task 不再接受输入");
        }
        if (input.kind() == ExecutionInput.Kind.SUPPLEMENT
                && task.status() != Status.AWAITING_CLARIFICATION) {
            throw new IllegalStateException("结构化参数只能补充当前待澄清 Task");
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
        return tasks.findByTenantIdAndTaskId(tenantId.value(), taskId.value()).map(this::stored);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Task> findTask(TenantId tenantId, TaskId taskId) {
        return tasks.findByTenantIdAndTaskId(tenantId.value(), taskId.value())
                .map(TaskRootEntity::getTask);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Task> findTasksByConversation(
            TenantId tenantId, UserId userId, ConversationId conversationId) {
        return tasks
                .findByTenantIdAndConversationIdAndUserIdOrderByIdDesc(
                        tenantId.value(), conversationId.value(), userId.value())
                .stream()
                .map(TaskRootEntity::getTask)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Execution> findExecution(TenantId tenantId, ExecutionId executionId) {
        return executions
                .findByTenantIdAndExecutionId(tenantId.value(), executionId.value())
                .map(TaskExecutionEntity::getExecution);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssistantCommand> findCommand(TenantId tenantId, ExecutionId executionId) {
        return executions
                .findByTenantIdAndExecutionId(tenantId.value(), executionId.value())
                .map(TaskExecutionEntity::getCommand);
    }

    @Override
    @Transactional
    public Execution recordSideEffectIntent(
            TenantId tenantId, ExecutionId executionId, Instant at) {
        Objects.requireNonNull(at, "at 不能为空");
        var entity =
                executions
                        .findForUpdate(tenantId.value(), executionId.value())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "side-effect intent 关联 Execution 不存在"));
        var changed = entity.getExecution().recordSideEffectIntent(at);
        entity.setExecution(changed);
        return executions.saveAndFlush(entity).getExecution();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Execution> findLatestNodeExecution(
            TenantId tenantId, TaskId taskId, String nodeId) {
        return executions
                .findFirstByTenantIdAndTaskIdAndNodeIdOrderByAttemptNoDesc(
                        tenantId.value(), taskId.value(), nodeId)
                .map(TaskExecutionEntity::getExecution);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskSnapshot> list(TenantId tenantId, UserId userId) {
        return tasks.findByTenantIdAndUserIdOrderByIdDesc(tenantId.value(), userId.value()).stream()
                .map(this::snapshot)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredTask> findPendingByConversation(
            TenantId tenantId, ConversationId conversationId) {
        return tasks
                .findByTenantIdAndConversationIdAndStatusOrderByIdAsc(
                        tenantId.value(), conversationId.value(), Status.READY.name())
                .stream()
                .map(this::stored)
                .toList();
    }

    @Override
    @Transactional
    public List<StoredTask> findDispatchable(Instant now, int limit) {
        if (limit < 1 || limit > 100)
            throw new IllegalArgumentException("dispatch limit 必须在 1..100");
        return dispatches
                .findDispatchableByExecutionScope(
                        Execution.Scope.TASK_ROOT.name(), now, PageRequest.of(0, limit))
                .stream()
                .map(
                        entity ->
                                executions
                                        .findByTenantIdAndExecutionId(
                                                entity.getTenantId(), entity.getExecutionId())
                                        .map(TaskExecutionEntity::getExecution)
                                        .flatMap(
                                                execution ->
                                                        tasks.findByTenantIdAndTaskId(
                                                                entity.getTenantId(),
                                                                execution.taskId().value()))
                                        .map(this::stored)
                                        .orElseThrow(
                                                () ->
                                                        new IllegalStateException(
                                                                "dispatch 缺少 Execution 或 Task")))
                .toList();
    }

    @Override
    @Transactional
    public Optional<StoredTask> claim(TenantId tenantId, TaskId taskId, Lease lease, Instant now) {
        leases.requireCurrent(lease);
        var taskEntity = tasks.findForUpdate(tenantId.value(), taskId.value()).orElse(null);
        if (taskEntity == null) return Optional.empty();
        var currentTask = taskEntity.getTask();
        if (currentTask.status() != Status.READY || currentTask.owner().kind() == OwnerKind.HUMAN) {
            return Optional.empty();
        }
        requireLeaseBoundary(currentTask, lease);
        var bundle = requireBundle(taskEntity);
        if (bundle.dispatch().getDispatch().nextRunAt().isAfter(now)
                || bundle.dispatch().getDispatch().status() != TaskDispatch.Status.PENDING) {
            return Optional.empty();
        }
        if (!currentTask.contract().deadline().isAfter(now)) {
            var pausedTask =
                    currentTask.withRuntime(
                            Status.PAUSED,
                            currentTask.owner(),
                            currentTask.budgetUsage(),
                            currentTask
                                    .checkpoint()
                                    .withAnnotation("deadlineReached", now.toString()),
                            currentTask.recoveryPoint(),
                            now);
            apply(taskEntity, pausedTask);
            apply(
                    bundle.execution(),
                    withExecutionStatus(
                            bundle.execution().getExecution(), Execution.Status.PAUSED, now),
                    bundle.execution().getCommand());
            var currentDispatch = bundle.dispatch().getDispatch();
            apply(
                    bundle.dispatch(),
                    new TaskDispatch(
                            currentDispatch.dispatchId(),
                            tenantId,
                            currentDispatch.executionId(),
                            TaskDispatch.Status.CANCELED,
                            currentDispatch.nextRunAt(),
                            null,
                            null,
                            currentDispatch.generation(),
                            currentDispatch.fencingToken(),
                            currentDispatch.deliveryAttempts(),
                            "deadline reached",
                            currentDispatch.version(),
                            currentDispatch.createdAt(),
                            now));
            flush(bundle, taskEntity);
            return Optional.empty();
        }
        var currentDispatch = bundle.dispatch().getDispatch();
        var generation = currentDispatch.generation();
        var runningTask =
                currentTask.withRuntime(
                        Status.RUNNING,
                        currentTask.owner(),
                        currentTask.budgetUsage(),
                        currentTask.checkpoint(),
                        currentTask.recoveryPoint(),
                        now);
        var runningExecution =
                withExecution(bundle.execution().getExecution(), Execution.Status.RUNNING, now);
        var leased =
                new TaskDispatch(
                        currentDispatch.dispatchId(),
                        tenantId,
                        runningExecution.executionId(),
                        TaskDispatch.Status.CLAIMED,
                        currentDispatch.nextRunAt(),
                        lease.ownerId(),
                        lease.expiresAt(),
                        generation,
                        lease.fencingToken(),
                        currentDispatch.deliveryAttempts() + 1,
                        currentDispatch.lastError(),
                        currentDispatch.version(),
                        currentDispatch.createdAt(),
                        now);
        apply(taskEntity, runningTask);
        apply(
                bundle.execution(),
                runningExecution,
                bundle.execution().getCommand().withLease(lease, now));
        apply(bundle.dispatch(), leased);
        flush(bundle, taskEntity);
        return Optional.of(stored(taskEntity));
    }

    @Override
    @Transactional
    public TaskSnapshot renew(TaskId taskId, Lease lease, Instant now) {
        leases.requireCurrent(lease);
        var taskEntity = requireTaskForUpdate(lease.tenantId(), taskId);
        var bundle = requireCurrent(taskEntity, lease, null);
        requireWithinDeadline(taskEntity, bundle, now);
        var current = bundle.dispatch().getDispatch();
        apply(
                bundle.dispatch(),
                new TaskDispatch(
                        current.dispatchId(),
                        lease.tenantId(),
                        current.executionId(),
                        TaskDispatch.Status.CLAIMED,
                        current.nextRunAt(),
                        lease.ownerId(),
                        lease.expiresAt(),
                        current.generation(),
                        current.fencingToken(),
                        current.deliveryAttempts(),
                        current.lastError(),
                        current.version(),
                        current.createdAt(),
                        now));
        dispatches.saveAndFlush(bundle.dispatch());
        return snapshot(taskEntity, bundle);
    }

    @Override
    @Transactional
    public void requireAgentExecution(InvocationContext context) {
        Objects.requireNonNull(context, "context 不能为空");
        if (context.controlMode()
                != com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode
                        .DELEGATED) {
            return;
        }
        requireLocked(context);
    }

    @Override
    @Transactional
    public <T> T accessAgentState(
            InvocationContext context, AgentStateAccess access, Supplier<T> operation) {
        Objects.requireNonNull(context, "context 不能为空");
        Objects.requireNonNull(access, "access 不能为空");
        Objects.requireNonNull(operation, "operation 不能为空");
        if (context.controlMode()
                != com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode
                        .DELEGATED) {
            return operation.get();
        }
        switch (access) {
            case ACTIVE -> requireLocked(context);
            case SUSPENDED_SNAPSHOT -> requireSuspendedStateSnapshot(context);
            case STALE_SAFE_CLEANUP -> requireStaleSafeStateCleanup(context);
        }
        return operation.get();
    }

    @Override
    @Transactional
    public void requireExecution(
            TenantId tenantId, TaskId taskId, ExecutionId executionId, Lease lease) {
        leases.requireCurrent(lease);
        var execution =
                executions
                        .findForUpdate(tenantId.value(), executionId.value())
                        .map(TaskExecutionEntity::getExecution)
                        .orElseThrow(() -> stale("Execution 不存在"));
        var dispatch =
                dispatches
                        .findActiveForUpdate(tenantId.value(), executionId.value())
                        .map(TaskDispatchEntity::getDispatch)
                        .orElseThrow(() -> stale("Execution 缺少 active dispatch"));
        if (!Objects.equals(execution.taskId(), taskId)
                || dispatch.status() != TaskDispatch.Status.CLAIMED
                || !Objects.equals(dispatch.leaseOwner(), lease.ownerId())
                || dispatch.leaseUntil() == null
                || !dispatch.leaseUntil().isAfter(Instant.now())) {
            throw stale("Execution 的 active dispatch identity 已失效");
        }
    }

    @Override
    @Transactional(noRollbackFor = BudgetExceededException.class)
    public Task reserveModelCall(InvocationContext context, Instant at) {
        return updateUsage(
                context,
                at,
                usage ->
                        new BudgetUsage(
                                usage.modelCalls() + 1,
                                usage.modelTokens(),
                                usage.toolCalls(),
                                usage.toolUnits(),
                                usage.credits()),
                (task, usage) -> usage.modelCalls() <= task.contract().maxModelCalls(),
                "模型调用次数越界");
    }

    @Override
    @Transactional(noRollbackFor = BudgetExceededException.class)
    public Task recordModelUsage(
            InvocationContext context, long tokens, BigDecimal credits, Instant at) {
        if (tokens < 0 || credits == null || credits.signum() < 0) {
            throw new IllegalArgumentException("模型预算增量不能为负数");
        }
        return updateUsage(
                context,
                at,
                usage ->
                        new BudgetUsage(
                                usage.modelCalls(),
                                usage.modelTokens() + tokens,
                                usage.toolCalls(),
                                usage.toolUnits(),
                                usage.credits().add(credits)),
                (task, usage) ->
                        usage.modelTokens() <= task.contract().budget().modelTokens()
                                && usage.credits().compareTo(task.contract().budget().credits())
                                        <= 0,
                "模型或积分预算越界");
    }

    @Override
    @Transactional(noRollbackFor = BudgetExceededException.class)
    public Task reserveToolCall(InvocationContext context, String action, Instant at) {
        var task = requireLocked(context).task().getTask();
        task.contract().requireAction(action);
        return updateUsage(
                context,
                at,
                usage ->
                        new BudgetUsage(
                                usage.modelCalls(),
                                usage.modelTokens(),
                                usage.toolCalls() + 1,
                                usage.toolUnits(),
                                usage.credits()),
                (candidate, usage) -> usage.toolCalls() <= candidate.contract().maxToolCalls(),
                "工具调用次数越界");
    }

    @Override
    @Transactional(noRollbackFor = BudgetExceededException.class)
    public Task recordToolUsage(
            InvocationContext context, long units, BigDecimal credits, Instant at) {
        if (units < 0 || credits == null || credits.signum() < 0) {
            throw new IllegalArgumentException("工具预算增量不能为负数");
        }
        return updateUsage(
                context,
                at,
                usage ->
                        new BudgetUsage(
                                usage.modelCalls(),
                                usage.modelTokens(),
                                usage.toolCalls(),
                                usage.toolUnits() + units,
                                usage.credits().add(credits)),
                (task, usage) ->
                        usage.toolUnits() <= task.contract().budget().toolUnits()
                                && usage.credits().compareTo(task.contract().budget().credits())
                                        <= 0,
                "工具或积分预算越界");
    }

    @Override
    @Transactional
    public TaskSnapshot checkpoint(
            InvocationContext context, TaskCheckpoint checkpoint, Instant nextRunAt, Instant at) {
        var locked = requireLocked(context);
        var task =
                locked.task()
                        .getTask()
                        .withRuntime(
                                locked.task().getTask().status(),
                                locked.task().getTask().owner(),
                                locked.task().getTask().budgetUsage(),
                                checkpoint,
                                locked.task().getTask().recoveryPoint(),
                                at);
        var dispatch = locked.bundle().dispatch().getDispatch();
        apply(locked.task(), task);
        apply(
                locked.bundle().dispatch(),
                new TaskDispatch(
                        dispatch.dispatchId(),
                        task.tenantId(),
                        dispatch.executionId(),
                        dispatch.status(),
                        nextRunAt,
                        dispatch.leaseOwner(),
                        dispatch.leaseUntil(),
                        dispatch.generation(),
                        dispatch.fencingToken(),
                        dispatch.deliveryAttempts(),
                        dispatch.lastError(),
                        dispatch.version(),
                        dispatch.createdAt(),
                        at));
        flush(locked.bundle(), locked.task());
        return snapshot(locked.task(), locked.bundle());
    }

    @Override
    @Transactional
    public TaskSnapshot complete(
            InvocationContext context, Map<String, Object> result, Instant at) {
        var locked = requireLocked(context);
        return finish(
                locked.task(),
                locked.bundle(),
                Status.COMPLETED,
                Execution.Status.COMPLETED,
                TaskDispatch.Status.DONE,
                locked.task().getTask().owner(),
                locked.task().getTask().checkpoint().withAnnotations(result),
                at);
    }

    @Override
    @Transactional
    public TaskSnapshot fail(InvocationContext context, String failure, Instant at) {
        var locked = requireLocked(context);
        return finish(
                locked.task(),
                locked.bundle(),
                Status.FAILED,
                Execution.Status.FAILED,
                TaskDispatch.Status.DONE,
                locked.task().getTask().owner(),
                locked.task().getTask().checkpoint().withAnnotation("lastFailure", failure),
                at);
    }

    @Override
    @Transactional
    public int recoverExpired(Instant now) {
        var expired =
                dispatches.findExpiredLeasesByExecutionScope(Execution.Scope.TASK_ROOT.name(), now);
        for (var dispatchEntity : expired) {
            var executionEntity =
                    executions
                            .findForUpdate(
                                    dispatchEntity.getTenantId(), dispatchEntity.getExecutionId())
                            .orElseThrow(
                                    () -> new IllegalStateException("过期 dispatch 缺少 execution"));
            var expiredExecution = executionEntity.getExecution();
            var taskEntity =
                    requireTaskForUpdate(
                            new TenantId(dispatchEntity.getTenantId()), expiredExecution.taskId());
            var task = taskEntity.getTask();
            if (task.currentRootExecutionId() == null
                    || !task.currentRootExecutionId()
                            .value()
                            .equals(dispatchEntity.getExecutionId())) {
                apply(
                        dispatchEntity,
                        terminalDispatch(
                                dispatchEntity.getDispatch(), TaskDispatch.Status.CANCELED, now));
                continue;
            }
            var recoveredTask =
                    task.withRuntime(
                            Status.READY,
                            task.owner(),
                            task.budgetUsage(),
                            task.checkpoint().withAnnotation("recoveredAt", now.toString()),
                            task.recoveryPoint(),
                            now);
            var execution =
                    withExecutionStatus(
                            executionEntity.getExecution(), Execution.Status.READY, now);
            var currentDispatch = dispatchEntity.getDispatch();
            var dispatch =
                    new TaskDispatch(
                            currentDispatch.dispatchId(),
                            task.tenantId(),
                            execution.executionId(),
                            TaskDispatch.Status.PENDING,
                            now,
                            null,
                            null,
                            currentDispatch.generation() + 1,
                            dispatches.nextFence(),
                            currentDispatch.deliveryAttempts(),
                            "lease expired",
                            currentDispatch.version(),
                            currentDispatch.createdAt(),
                            now);
            apply(taskEntity, recoveredTask);
            apply(executionEntity, execution, executionEntity.getCommand().asResume(now));
            apply(dispatchEntity, dispatch);
            tasks.save(taskEntity);
            executions.save(executionEntity);
            dispatches.save(dispatchEntity);
        }
        dispatches.flush();
        return expired.size();
    }

    private Task updateUsage(
            InvocationContext context,
            Instant at,
            java.util.function.UnaryOperator<BudgetUsage> mutation,
            java.util.function.BiPredicate<Task, BudgetUsage> withinLimit,
            String failure) {
        var locked = requireLocked(context);
        requireWithinDeadline(locked.task(), locked.bundle(), at);
        var current = locked.task().getTask();
        var usage = mutation.apply(current.budgetUsage());
        var nextStatus = withinLimit.test(current, usage) ? current.status() : Status.PAUSED;
        var task =
                current.withRuntime(
                        nextStatus,
                        current.owner(),
                        usage,
                        nextStatus == Status.PAUSED
                                ? current.checkpoint().withAnnotation("budgetPause", failure)
                                : current.checkpoint(),
                        current.recoveryPoint(),
                        at);
        apply(locked.task(), task);
        if (nextStatus == Status.PAUSED) {
            apply(
                    locked.bundle().execution(),
                    withExecutionStatus(
                            locked.bundle().execution().getExecution(),
                            Execution.Status.PAUSED,
                            at),
                    locked.bundle().execution().getCommand());
            apply(
                    locked.bundle().dispatch(),
                    terminalDispatch(
                            locked.bundle().dispatch().getDispatch(),
                            TaskDispatch.Status.CANCELED,
                            at));
        }
        flush(locked.bundle(), locked.task());
        if (nextStatus == Status.PAUSED) throw new BudgetExceededException(failure);
        return task;
    }

    private TaskSnapshot finish(
            TaskRootEntity taskEntity,
            Bundle bundle,
            Status taskStatus,
            Execution.Status executionStatus,
            TaskDispatch.Status dispatchStatus,
            Owner owner,
            TaskCheckpoint checkpoint,
            Instant at) {
        var task =
                taskEntity
                        .getTask()
                        .withRuntime(
                                taskStatus,
                                owner,
                                taskEntity.getTask().budgetUsage(),
                                checkpoint,
                                taskEntity.getTask().recoveryPoint(),
                                at);
        var execution = withExecutionStatus(bundle.execution().getExecution(), executionStatus, at);
        var dispatch = terminalDispatch(bundle.dispatch().getDispatch(), dispatchStatus, at);
        apply(taskEntity, task);
        apply(bundle.execution(), execution, bundle.execution().getCommand());
        apply(bundle.dispatch(), dispatch);
        flush(bundle, taskEntity);
        return new TaskSnapshot(task, execution, dispatch);
    }

    private void requireSuspendedStateSnapshot(InvocationContext context) {
        var task = requireTaskForUpdate(context.tenantId(), context.taskId());
        var execution =
                executions
                        .findForUpdate(context.tenantId().value(), context.executionId().value())
                        .orElseThrow(() -> stale("Execution 不存在"));
        var dispatch =
                dispatches
                        .findForUpdateByTenantIdAndDispatchId(
                                context.tenantId().value(), context.dispatchId())
                        .orElseThrow(() -> stale("原 Dispatch 不存在"));
        requireStateAccessIdentity(context, task.getTask(), execution.getExecution(), dispatch.getDispatch());
        var taskStatus = task.getTask().status();
        var executionStatus = execution.getExecution().status();
        var waiting =
                (taskStatus == Status.AWAITING_AUTHORIZATION
                                && executionStatus == Execution.Status.AWAITING_AUTHORIZATION)
                        || (taskStatus == Status.AWAITING_CLARIFICATION
                                && executionStatus == Execution.Status.AWAITING_CLARIFICATION);
        if (!waiting
                || dispatch.getDispatch().status() != TaskDispatch.Status.CANCELED
                || dispatches
                        .findActiveForUpdate(
                                context.tenantId().value(), context.executionId().value())
                        .isPresent()) {
            throw stale("HITL 状态快照已失去原 waiting/dispatch 权限");
        }
    }

    private void requireStaleSafeStateCleanup(InvocationContext context) {
        var task = requireTaskForUpdate(context.tenantId(), context.taskId());
        var execution =
                executions
                        .findForUpdate(context.tenantId().value(), context.executionId().value())
                        .orElseThrow(() -> stale("Execution 不存在"));
        var dispatch =
                dispatches
                        .findForUpdateByTenantIdAndDispatchId(
                                context.tenantId().value(), context.dispatchId())
                        .orElseThrow(() -> stale("原 Dispatch 不存在"));
        requireStateAccessIdentity(context, task.getTask(), execution.getExecution(), dispatch.getDispatch());
        var active =
                dispatches.findActiveForUpdate(
                        context.tenantId().value(), context.executionId().value());
        if (active.isPresent()) {
            var current = active.orElseThrow().getDispatch();
            if (!current.accepts(
                    context.dispatchId(),
                    context.dispatchGeneration(),
                    context.dispatchFencingToken(),
                    context.dispatchLeaseOwner(),
                    Instant.now())) {
                throw stale("新 Dispatch 已接管状态槽，旧 worker 禁止清理");
            }
            return;
        }
        var latest =
                dispatches
                        .findFirstByTenantIdAndExecutionIdOrderByIdDesc(
                                context.tenantId().value(), context.executionId().value())
                        .map(TaskDispatchEntity::getDispatch)
                        .orElseThrow(() -> stale("Execution 缺少 Dispatch 历史"));
        if (!sameDispatchIdentity(context, latest)) {
            throw stale("状态槽已有更新 Dispatch，旧 worker 禁止清理");
        }
    }

    private static void requireStateAccessIdentity(
            InvocationContext context, Task task, Execution execution, TaskDispatch dispatch) {
        if (!Objects.equals(execution.taskId(), context.taskId())
                || !execution.executionId().equals(context.executionId())
                || !execution.stateSlotId().equals(context.stateSlotId())
                || !Objects.equals(task.currentPlanId(), execution.planId())
                || !Objects.equals(task.currentPlanRevision(), execution.planRevision())
                || context.nodeIdentity() == null
                || !Objects.equals(execution.nodeId(), context.nodeIdentity().nodeId())
                || !sameDispatchIdentity(context, dispatch)) {
            throw stale("AgentState task/plan/node/execution/stateSlot/dispatch 身份不一致");
        }
    }

    private static boolean sameDispatchIdentity(
            InvocationContext context, TaskDispatch dispatch) {
        return dispatch.executionId().equals(context.executionId())
                && dispatch.dispatchId().equals(context.dispatchId())
                && dispatch.generation() == context.dispatchGeneration()
                && dispatch.fencingToken() == context.dispatchFencingToken();
    }

    private Locked requireLocked(InvocationContext context) {
        leases.requireCurrent(context.lease());
        var task = requireTaskForUpdate(context.tenantId(), context.taskId());
        var execution =
                executions
                        .findForUpdate(context.tenantId().value(), context.executionId().value())
                        .orElseThrow(() -> stale("Execution 不存在"));
        var dispatch =
                dispatches
                        .findActiveForUpdate(
                                context.tenantId().value(), context.executionId().value())
                        .orElseThrow(() -> stale("Execution 缺少 active dispatch"));
        var executionFact = execution.getExecution();
        var dispatchFact = dispatch.getDispatch();
        if (!Objects.equals(executionFact.taskId(), context.taskId())
                || !executionFact.stateSlotId().equals(context.stateSlotId())
                || !Objects.equals(task.getTask().currentPlanId(), executionFact.planId())
                || !Objects.equals(
                        task.getTask().currentPlanRevision(), executionFact.planRevision())
                || executionFact.status() != Execution.Status.RUNNING
                || context.nodeIdentity() == null
                || !Objects.equals(executionFact.nodeId(), context.nodeIdentity().nodeId())
                || !dispatchFact.accepts(
                        context.dispatchId(),
                        context.dispatchGeneration(),
                        context.dispatchFencingToken(),
                        context.dispatchLeaseOwner(),
                        Instant.now())) {
            throw stale("旧 plan/node/execution/dispatch 禁止提交");
        }
        return new Locked(task, new Bundle(execution, dispatch));
    }

    private Bundle requireCurrent(TaskRootEntity taskEntity, Lease lease, ExecutionId executionId) {
        requireLeaseBoundary(taskEntity.getTask(), lease);
        var bundle = requireBundle(taskEntity);
        var execution = bundle.execution().getExecution();
        var dispatch = bundle.dispatch().getDispatch();
        if (taskEntity.getTask().status() != Status.RUNNING
                || taskEntity.getTask().owner().kind() == OwnerKind.HUMAN
                || dispatch.status() != TaskDispatch.Status.CLAIMED
                || dispatch.fencingToken() != lease.fencingToken()
                || !Objects.equals(dispatch.leaseOwner(), lease.ownerId())
                || (executionId != null && !execution.executionId().equals(executionId))) {
            throw stale("旧 owner、fence 或 execution 禁止写 Task");
        }
        return bundle;
    }

    private static void requireNodeIdentity(
            InvocationContext parentContext, Execution execution, AssistantCommand command) {
        if (execution.scope() != Execution.Scope.TASK_NODE
                || !execution.tenantId().equals(parentContext.tenantId())
                || !Objects.equals(execution.taskId(), parentContext.taskId())
                || execution.planId() == null
                || execution.planRevision() == null
                || !Objects.equals(execution.parentExecutionId(), parentContext.executionId())
                || execution.status() != Execution.Status.RUNNING
                || !command.tenantId().equals(execution.tenantId())
                || !Objects.equals(command.taskId(), execution.taskId())
                || !command.executionId().equals(execution.executionId())
                || !Objects.equals(command.parentExecutionId(), execution.parentExecutionId())
                || command.nodeIdentity() == null
                || !Objects.equals(command.nodeIdentity().nodeId(), execution.nodeId())) {
            throw stale("TaskNode task/plan/node/parent identity 不一致");
        }
    }

    private static void requireSameNodeAttempt(Execution current, Execution requested) {
        if (current.scope() != Execution.Scope.TASK_NODE
                || !current.tenantId().equals(requested.tenantId())
                || !current.userId().equals(requested.userId())
                || !current.conversationId().equals(requested.conversationId())
                || !Objects.equals(current.taskId(), requested.taskId())
                || !Objects.equals(current.planId(), requested.planId())
                || !Objects.equals(current.planRevision(), requested.planRevision())
                || !Objects.equals(current.nodeId(), requested.nodeId())
                || !current.executionId().equals(requested.executionId())
                || !current.sessionId().equals(requested.sessionId())
                || !current.runId().equals(requested.runId())
                || !current.correlationId().equals(requested.correlationId())
                || !Objects.equals(current.parentExecutionId(), requested.parentExecutionId())
                || current.attemptNo() != requested.attemptNo()
                || !current.stateSlotId().equals(requested.stateSlotId())) {
            throw stale("executionId 已绑定不同 TaskNode attempt");
        }
    }

    private void requireWithinDeadline(TaskRootEntity taskEntity, Bundle bundle, Instant at) {
        if (!taskEntity.getTask().contract().deadline().isAfter(at)) {
            var task =
                    taskEntity
                            .getTask()
                            .withRuntime(
                                    Status.PAUSED,
                                    taskEntity.getTask().owner(),
                                    taskEntity.getTask().budgetUsage(),
                                    taskEntity
                                            .getTask()
                                            .checkpoint()
                                            .withAnnotation("deadlineReached", at.toString()),
                                    taskEntity.getTask().recoveryPoint(),
                                    at);
            apply(taskEntity, task);
            apply(
                    bundle.execution(),
                    withExecutionStatus(
                            bundle.execution().getExecution(), Execution.Status.PAUSED, at),
                    bundle.execution().getCommand());
            apply(
                    bundle.dispatch(),
                    terminalDispatch(
                            bundle.dispatch().getDispatch(), TaskDispatch.Status.CANCELED, at));
            flush(bundle, taskEntity);
            throw new BudgetExceededException("Task 已到 deadline");
        }
    }

    private TaskSnapshot requireOwned(TenantId tenantId, UserId userId, TaskId taskId) {
        return snapshot(requireOwnedEntity(tenantId, userId, taskId));
    }

    private TaskRootEntity requireOwnedEntity(TenantId tenantId, UserId userId, TaskId taskId) {
        var entity = requireTaskForUpdate(tenantId, taskId);
        if (!entity.getUserId().equals(userId.value())) {
            throw new IllegalArgumentException("Task 不属于当前用户");
        }
        return entity;
    }

    private TaskRootEntity requireTaskForUpdate(TenantId tenantId, TaskId taskId) {
        return tasks.findForUpdate(tenantId.value(), taskId.value())
                .orElseThrow(() -> new IllegalArgumentException("Task 不存在: " + taskId.value()));
    }

    private void supersedePrevious(TaskSnapshot next, String previousExecutionId) {
        var previousExecution =
                executions
                        .findForUpdate(next.tenantId().value(), previousExecutionId)
                        .orElseThrow(
                                () -> new IllegalStateException("current-root 前驱 Execution 不存在"));
        apply(
                previousExecution,
                withExecutionStatus(
                        previousExecution.getExecution(),
                        Execution.Status.SUPERSEDED,
                        next.updatedAt()),
                previousExecution.getCommand());
        executions.save(previousExecution);

        var previousDispatch =
                dispatches
                        .findActiveForUpdate(next.tenantId().value(), previousExecutionId)
                        .orElseThrow(
                                () -> new IllegalStateException("current-root 前驱 Dispatch 不存在"));
        apply(
                previousDispatch,
                terminalDispatch(
                        previousDispatch.getDispatch(),
                        TaskDispatch.Status.CANCELED,
                        next.updatedAt()));
        dispatches.save(previousDispatch);
    }

    private Bundle requireBundle(TaskRootEntity taskEntity) {
        var task = taskEntity.getTask();
        var current = task.currentRootExecutionId();
        if (current == null) throw new IllegalStateException("Task 缺少 currentRootExecutionId");
        var execution =
                executions
                        .findForUpdate(task.tenantId().value(), current.value())
                        .orElseThrow(
                                () -> new IllegalStateException("Task 缺少 current root Execution"));
        var dispatch =
                dispatches
                        .findActiveForUpdate(task.tenantId().value(), current.value())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Task 缺少 current root TaskDispatch"));
        return new Bundle(execution, dispatch);
    }

    private StoredTask stored(TaskRootEntity taskEntity) {
        var bundle = requireBundle(taskEntity);
        return new StoredTask(snapshot(taskEntity, bundle), bundle.execution().getCommand());
    }

    private TaskSnapshot snapshot(TaskRootEntity taskEntity) {
        return snapshot(taskEntity, requireBundle(taskEntity));
    }

    private static TaskSnapshot snapshot(TaskRootEntity taskEntity, Bundle bundle) {
        return new TaskSnapshot(
                taskEntity.getTask(),
                bundle.execution().getExecution(),
                bundle.dispatch().getDispatch());
    }

    private void flush(Bundle bundle, TaskRootEntity taskEntity) {
        tasks.save(taskEntity);
        executions.save(bundle.execution());
        dispatches.save(bundle.dispatch());
        dispatches.flush();
    }

    private static void requireLeaseBoundary(Task task, Lease lease) {
        if (!task.tenantId().equals(lease.tenantId())
                || !task.conversationId().equals(lease.conversationId())) {
            throw stale("conversation lease 与 Task 边界不一致");
        }
    }

    private static void apply(TaskRootEntity entity, Task task) {
        entity.setTask(task);
    }

    private static void apply(
            TaskExecutionEntity entity, Execution execution, AssistantCommand command) {
        entity.setExecution(execution);
        entity.setCommand(command);
    }

    private static void apply(TaskDispatchEntity entity, TaskDispatch dispatch) {
        entity.setDispatch(dispatch);
    }

    private static Execution withExecutionStatus(
            Execution source, Execution.Status status, Instant at) {
        return withExecution(source, status, at);
    }

    private static Execution withExecution(Execution source, Execution.Status status, Instant at) {
        return new Execution(
                source.tenantId(),
                source.userId(),
                source.conversationId(),
                source.taskId(),
                source.planId(),
                source.planRevision(),
                source.nodeId(),
                source.executionId(),
                source.sessionId(),
                source.runId(),
                source.correlationId(),
                source.parentExecutionId(),
                source.predecessorExecutionId(),
                source.scope(),
                source.attemptNo(),
                source.stateSlotId(),
                status,
                source.promotionState(),
                source.sideEffectEpoch(),
                source.ownerSnapshot(),
                status == Execution.Status.FAILED
                        ? source.consecutiveFailures() + 1
                        : source.consecutiveFailures(),
                source.createdAt(),
                at);
    }

    private static TaskDispatch terminalDispatch(
            TaskDispatch source, TaskDispatch.Status status, Instant at) {
        return new TaskDispatch(
                source.dispatchId(),
                source.tenantId(),
                source.executionId(),
                status,
                source.nextRunAt(),
                null,
                null,
                source.generation(),
                source.fencingToken(),
                source.deliveryAttempts(),
                source.lastError(),
                source.version(),
                source.createdAt(),
                at);
    }

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static StaleExecutionException stale(String message) {
        return new StaleExecutionException(message);
    }

    private record Bundle(TaskExecutionEntity execution, TaskDispatchEntity dispatch) {}

    private record Locked(TaskRootEntity task, Bundle bundle) {}
}
