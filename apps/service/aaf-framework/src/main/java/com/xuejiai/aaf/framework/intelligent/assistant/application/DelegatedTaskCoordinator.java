package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.xuejiai.aaf.framework.engine.task.agent.AgentTaskRuntime;
import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentExecutionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.BudgetUsage;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.Owner;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.OwnerKind;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.Source;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.Status;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionContract;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionContract.NotificationTrigger;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionContract.ResponsibleOwner;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard.SubTask;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort.Lease;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskDispatchPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.NotificationPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.NotificationPort.Notification;
import com.xuejiai.aaf.framework.intelligent.assistant.port.NotificationPort.Type;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskBoardPort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** DELEGATED 唯一应用入口；任务、DAG 与预算事实均在 PostgreSQL。 */
public final class DelegatedTaskCoordinator {
    private static final String DELEGATED_TASK_TYPE = "delegated-task";
    private static final Set<String> CONVERSATION_ALLOWED_ACTIONS =
            Set.of("knowledge.search", "content.generate");

    private final DelegatedTaskPort tasks;
    private final TaskBoardPort boards;
    private final ConversationLeasePort leases;
    private final AssistantCommandPort commands;
    private final AgentExecutionPort agentExecution;
    private final ExecutionEventStorePort events;
    private final NotificationPort notifications;
    private final DelegatedTaskDispatchPort dispatch;
    private final AgentTaskRuntime agentTaskRuntime;
    private final Clock clock;
    private final Duration leaseTtl;

    public DelegatedTaskCoordinator(
            DelegatedTaskPort tasks,
            TaskBoardPort boards,
            ConversationLeasePort leases,
            AssistantCommandPort commands,
            AgentExecutionPort agentExecution,
            ExecutionEventStorePort events,
            NotificationPort notifications,
            DelegatedTaskDispatchPort dispatch,
            AgentTaskRuntime agentTaskRuntime,
            Clock clock,
            Duration leaseTtl) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
        this.boards = Objects.requireNonNull(boards, "boards 不能为空");
        this.leases = Objects.requireNonNull(leases, "leases 不能为空");
        this.commands = Objects.requireNonNull(commands, "commands 不能为空");
        this.agentExecution = Objects.requireNonNull(agentExecution, "agentExecution 不能为空");
        this.events = Objects.requireNonNull(events, "events 不能为空");
        this.notifications = Objects.requireNonNull(notifications, "notifications 不能为空");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch 不能为空");
        this.agentTaskRuntime = Objects.requireNonNull(agentTaskRuntime, "agentTaskRuntime 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
        this.leaseTtl = Objects.requireNonNull(leaseTtl, "leaseTtl 不能为空");
        if (leaseTtl.isZero() || leaseTtl.isNegative()) {
            throw new IllegalArgumentException("leaseTtl 必须为正数");
        }
    }

    public DelegatedTask submit(AssistantCommand command) {
        var contract =
                Objects.requireNonNull(command.executionContract(), "ExecutionContract 不能为空");
        return submit(
                command,
                TaskBoard.single(
                        command.taskId(), command.input(), contract.retryPolicy().maxAttempts()),
                Source.AUTOMATION,
                0);
    }

    public DelegatedTask submit(AssistantCommand command, TaskBoard board) {
        return submit(command, board, Source.AUTOMATION, 0);
    }

    public DelegatedTask submitConversationTask(
            AssistantCommand command, String title, String description, int priority) {
        var effectiveCommand = requireTaskCommand(command, title, null);
        var board =
                TaskBoard.single(effectiveCommand.taskId(), taskDescription(title, description), 3);
        return submit(effectiveCommand, board, Source.CONVERSATION, priority);
    }

    public DelegatedTask submitManualTask(
            AssistantCommand command,
            String title,
            String description,
            int priority,
            ExecutionContract contract) {
        var effectiveCommand = requireTaskCommand(command, title, contract);
        var board =
                TaskBoard.single(
                        effectiveCommand.taskId(),
                        taskDescription(title, description),
                        effectiveCommand.executionContract().retryPolicy().maxAttempts());
        return submit(effectiveCommand, board, Source.MANUAL, priority);
    }

    private DelegatedTask submit(
            AssistantCommand command, TaskBoard board, Source source, int priority) {
        Objects.requireNonNull(command, "command 不能为空");
        Objects.requireNonNull(board, "board 不能为空");
        Objects.requireNonNull(source, "source 不能为空");
        if (command.controlMode() != ExecutionEvent.ControlMode.DELEGATED
                || command.executionContract() == null
                || command.operation() != AssistantCommand.Operation.START) {
            throw new IllegalArgumentException("委托任务必须由携带完整 ExecutionContract 的 START 命令创建");
        }
        if (!command.taskId().equals(board.taskId())) {
            throw new IllegalArgumentException("TaskBoard 与委托命令 taskId 不一致");
        }
        var at = command.requestedAt();
        var task =
                new DelegatedTask(
                        command.tenantId(),
                        command.userId(),
                        command.taskId(),
                        command.conversationId(),
                        command.sessionId(),
                        command.executionId(),
                        command.parentExecutionId(),
                        source,
                        priority,
                        Status.PENDING,
                        new Owner(OwnerKind.ASSISTANT, command.assistantId().value()),
                        command.executionContract(),
                        BudgetUsage.empty(),
                        0,
                        0,
                        at,
                        null,
                        null,
                        0,
                        Map.of(),
                        at,
                        at);
        var stored = tasks.create(task, command, board);
        notifyStatus(stored.task(), "委托任务已进入持久调度队列", "submitted");
        dispatch.signal(command.tenantId(), command.taskId());
        return stored.task();
    }

    private AssistantCommand requireTaskCommand(
            AssistantCommand command, String title, ExecutionContract contract) {
        Objects.requireNonNull(command, "command 不能为空");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("任务标题不能为空白");
        }
        var effectiveContract =
                contract == null
                        ? ExecutionContract.conversationDefault(
                                CONVERSATION_ALLOWED_ACTIONS,
                                new ResponsibleOwner("ASSISTANT", command.assistantId().value()))
                        : contract;
        return new AssistantCommand(
                command.operation(),
                command.tenantId(),
                command.userId(),
                command.memorySubject(),
                command.assistantId(),
                command.assistantVersion(),
                command.conversationId(),
                command.sessionId(),
                command.taskId(),
                command.executionId(),
                command.runId(),
                command.parentExecutionId(),
                command.correlationId(),
                command.causationId(),
                command.idempotencyKey(),
                ExecutionEvent.ControlMode.DELEGATED,
                effectiveContract,
                command.lease(),
                command.sequenceBase(),
                command.input(),
                command.completionCriteria(),
                command.contextCandidates(),
                command.requestedAt());
    }

    private static String taskDescription(String title, String description) {
        return description == null || description.isBlank()
                ? title
                : title + "\n\n---\n\n" + description;
    }

    public static TitleDescription splitGoalDescription(String raw) {
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

    public record TitleDescription(String title, String description) {}

    public Flux<ExecutionEvent> dispatch(TenantId tenantId, TaskId taskId, String workerId) {
        var stored =
                tasks.find(tenantId, taskId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("委托任务不存在: " + taskId.value()));
        var board = requireBoard(stored);
        var lease =
                leases.acquire(tenantId, stored.task().conversationId(), workerId, leaseTtl)
                        .orElse(null);
        if (lease == null) return Flux.empty();
        var claimed = tasks.claim(tenantId, taskId, lease, clock.instant()).orElse(null);
        if (claimed == null) {
            leases.release(lease);
            notifyDeadlinePauseIfNeeded(tenantId, taskId);
            return Flux.empty();
        }
        var command = claimed.command().withLease(lease, clock.instant());
        var parentContext = context(command);
        if (board.hasRunning()) {
            board = boards.interruptRunning(tenantId, taskId, true, lease);
        }
        notifyStatus(claimed.task(), "委托任务开始执行", "running-" + lease.fencingToken());
        return withHeartbeat(
                        executeBoard(command, parentContext, board.maxParallelism()), taskId, lease)
                .doOnError(failure -> failParent(parentContext, failure))
                .doFinally(
                        ignored -> {
                            leases.release(lease);
                            tasks.find(tenantId, taskId)
                                    .map(DelegatedTaskPort.StoredTask::task)
                                    .ifPresent(
                                            latest -> {
                                                if (latest.status() == Status.PENDING) {
                                                    dispatch.signal(tenantId, taskId);
                                                } else if (latest.status() == Status.PAUSED) {
                                                    notifyStatus(
                                                            latest,
                                                            "委托任务因停止条件暂停",
                                                            "boundary-paused-"
                                                                    + latest.fencingToken());
                                                } else if (latest.status() == Status.COMPLETED
                                                        || latest.status() == Status.FAILED) {
                                                    dispatchNextInConversation(
                                                            tenantId, latest.conversationId());
                                                }
                                            });
                        });
    }

    public void dispatchNextInConversation(TenantId tenantId, ConversationId conversationId) {
        tasks.findPendingByConversation(tenantId, conversationId).stream()
                .sorted(
                        Comparator.comparingInt(
                                        (DelegatedTaskPort.StoredTask stored) ->
                                                stored.task().priority())
                                .thenComparing(stored -> stored.task().createdAt()))
                .findFirst()
                .ifPresent(
                        stored ->
                                agentTaskRuntime.dispatch(
                                        DELEGATED_TASK_TYPE,
                                        stored.task().taskId().value(),
                                        tenantId.value(),
                                        "QUEUE"));
    }

    public DelegatedTask stop(TenantId tenantId, UserId userId, TaskId taskId, String reason) {
        var stored = requireOwned(tenantId, userId, taskId);
        var lease =
                leases.preempt(
                        tenantId, stored.task().conversationId(), "stop-" + randomId(), leaseTtl);
        try {
            cancelRunningChildren(tenantId, taskId);
            boards.interruptRunning(tenantId, taskId, false, lease);
            var canceled = tasks.cancel(tenantId, userId, taskId, reason, lease, clock.instant());
            notifyStatus(canceled, "任务已停止", "stopped");
            return canceled;
        } finally {
            leases.release(lease);
        }
    }

    public DelegatedTask takeOver(TenantId tenantId, UserId userId, TaskId taskId, String reason) {
        var stored = requireOwned(tenantId, userId, taskId);
        var lease =
                leases.preempt(
                        tenantId,
                        stored.task().conversationId(),
                        "takeover-" + randomId(),
                        leaseTtl);
        try {
            cancelRunningChildren(tenantId, taskId);
            boards.interruptRunning(tenantId, taskId, true, lease);
            var taken = tasks.takeOver(tenantId, userId, taskId, reason, lease, clock.instant());
            notifyStatus(taken, "人工已接管，Agent 工具网关关闭", "human-takeover");
            return taken;
        } finally {
            leases.release(lease);
        }
    }

    public DelegatedTask handBack(TenantId tenantId, UserId userId, TaskId taskId) {
        var stored = requireOwned(tenantId, userId, taskId);
        var lease =
                leases.acquire(
                                tenantId,
                                stored.task().conversationId(),
                                "hand-back-" + randomId(),
                                leaseTtl)
                        .orElseThrow(() -> new IllegalStateException("conversation 当前由其他副本持有"));
        final DelegatedTask returned;
        try {
            returned =
                    tasks.handBack(
                                    tenantId,
                                    userId,
                                    taskId,
                                    new ExecutionId(randomId()),
                                    new SessionId(randomId()),
                                    lease,
                                    clock.instant())
                            .task();
        } finally {
            leases.release(lease);
        }
        notifyStatus(returned, "人工已交回，创建新的 execution", "hand-back");
        dispatch.signal(tenantId, taskId);
        return returned;
    }

    public Mono<DelegatedTask> acceptInput(ExecutionInput input) {
        var stored = requireOwned(input.tenantId(), input.userId(), input.taskId());
        if (input.kind() == ExecutionInput.Kind.UNRELATED) {
            var leaseUse = diagnosticOrAcquiredLease(stored.task(), "unrelated-" + randomId());
            try {
                return appendInputEvent(
                                stored.command().withLease(leaseUse.lease(), input.receivedAt()),
                                input,
                                leaseUse.lease())
                        .map(ignored -> stored.task())
                        .doFinally(
                                ignored -> {
                                    if (leaseUse.acquired()) leases.release(leaseUse.lease());
                                });
            } catch (RuntimeException failure) {
                if (leaseUse.acquired()) leases.release(leaseUse.lease());
                throw failure;
            }
        }
        var lease =
                leases.preempt(
                        input.tenantId(),
                        stored.task().conversationId(),
                        "input-" + randomId(),
                        leaseTtl);
        var released = new AtomicBoolean();
        try {
            cancelRunningChildren(input.tenantId(), input.taskId());
            boards.interruptRunning(
                    input.tenantId(),
                    input.taskId(),
                    input.kind() != ExecutionInput.Kind.CANCEL,
                    lease);
            var changed = tasks.applyInput(input, lease);
            return appendInputEvent(
                            stored.command().withLease(lease, input.receivedAt()), input, lease)
                    .map(ignored -> changed)
                    .doOnSuccess(
                            task -> {
                                releaseOnce(lease, released);
                                if (input.kind() == ExecutionInput.Kind.MODIFY
                                        || input.kind() == ExecutionInput.Kind.SUPPLEMENT) {
                                    dispatch.signal(input.tenantId(), input.taskId());
                                }
                            })
                    .doFinally(ignored -> releaseOnce(lease, released));
        } catch (RuntimeException failure) {
            releaseOnce(lease, released);
            throw failure;
        }
    }

    public int recoverAndDispatch(String workerId, int limit) {
        var recovered = tasks.recoverExpired(clock.instant());
        try {
            notifications.retryFailed(Math.min(limit, 100));
        } catch (RuntimeException ignored) {
            // 通知 outbox 故障不改变任务事实或阻断任务恢复。
        }
        tasks.findDispatchable(clock.instant(), limit)
                .forEach(
                        stored ->
                                dispatch.signal(stored.task().tenantId(), stored.task().taskId()));
        return recovered;
    }

    private Flux<ExecutionEvent> executeBoard(
            AssistantCommand parentCommand, InvocationContext parentContext, int parallelism) {
        return Flux.defer(
                () -> {
                    var latest =
                            tasks.find(parentContext.tenantId(), parentContext.taskId())
                                    .map(DelegatedTaskPort.StoredTask::task)
                                    .orElseThrow(() -> new IllegalStateException("委托任务已丢失"));
                    if (latest.status() != Status.RUNNING) return Flux.empty();
                    var claim =
                            boards.claimReady(
                                    parentContext.tenantId(),
                                    parentContext.taskId(),
                                    parentContext.lease());
                    if (claim.subTasks().isEmpty()) {
                        return finalizeBoard(parentContext, claim.board());
                    }
                    return Flux.fromIterable(claim.subTasks())
                            .flatMap(
                                    subTask ->
                                            executeSubTask(parentCommand, parentContext, subTask),
                                    parallelism)
                            .concatWith(
                                    Flux.defer(
                                            () ->
                                                    executeBoard(
                                                            parentCommand,
                                                            parentContext,
                                                            parallelism)));
                });
    }

    private Flux<ExecutionEvent> executeSubTask(
            AssistantCommand parentCommand, InvocationContext parentContext, SubTask subTask) {
        var childCommand =
                parentCommand.forSubTask(subTask, parentCommand.lease(), clock.instant());
        var result = new AtomicReference<>("");
        var failure = new AtomicReference<String>();
        var completed = new AtomicBoolean();
        var awaitingAuthorization = new AtomicBoolean();
        return commands.execute(childCommand)
                .doOnNext(
                        event -> {
                            notifyBudgetIfNeeded(parentContext);
                            if (event.type() == ExecutionEventType.MESSAGE_COMPLETED) {
                                var text = event.payload().values().get("text");
                                if (text != null) result.set(text.toString());
                            }
                            if (event.type() == ExecutionEventType.EXECUTION_COMPLETED)
                                completed.set(true);
                            if (event.status() == ExecutionEventStatus.AWAITING_AUTHORIZATION) {
                                awaitingAuthorization.set(true);
                                tasks.awaitAuthorization(
                                        parentContext, "子任务等待工具授权", clock.instant());
                                notify(
                                        parentContext,
                                        Type.AUTHORIZATION_GAP,
                                        "任务等待授权",
                                        event.eventId().value(),
                                        event.payload().values());
                            }
                            if (event.type() == ExecutionEventType.EXECUTION_FAILED
                                    || event.type() == ExecutionEventType.COMMAND_REJECTED
                                    || event.status() == ExecutionEventStatus.FAILED
                                    || event.status() == ExecutionEventStatus.REJECTED) {
                                failure.compareAndSet(null, event.payload().values().toString());
                            }
                        })
                .takeUntil(event -> awaitingAuthorization.get())
                .onErrorResume(
                        error -> {
                            failure.compareAndSet(
                                    null, Objects.requireNonNullElse(error.getMessage(), "子任务失败"));
                            return Flux.empty();
                        })
                .concatWith(
                        Flux.defer(
                                () -> {
                                    if (awaitingAuthorization.get()) {
                                        boards.interruptSubTask(
                                                parentContext.tenantId(),
                                                parentContext.taskId(),
                                                subTask.subTaskId(),
                                                true,
                                                parentContext.lease());
                                        return Flux.empty();
                                    }
                                    if (failure.get() != null || !completed.get()) {
                                        boards.failSubTask(
                                                parentContext.tenantId(),
                                                parentContext.taskId(),
                                                subTask.subTaskId(),
                                                Objects.requireNonNullElse(
                                                        failure.get(), "子任务未产生完整终态"),
                                                failure.get() == null
                                                        || isTransientMessage(failure.get()),
                                                parentContext.lease());
                                    } else {
                                        boards.completeSubTask(
                                                parentContext.tenantId(),
                                                parentContext.taskId(),
                                                subTask.subTaskId(),
                                                result.get(),
                                                parentContext.lease());
                                    }
                                    return Flux.empty();
                                }));
    }

    private Flux<ExecutionEvent> finalizeBoard(InvocationContext context, TaskBoard board) {
        if (board.completed()) {
            var results = new LinkedHashMap<String, Object>();
            board.subTasks().forEach((id, subTask) -> results.put(id, subTask.result()));
            var completed = tasks.complete(context, results, clock.instant());
            notifyStatus(completed, "委托任务完成", "completed");
            return Flux.empty();
        }
        if (board.hasTerminalFailure()) {
            var failed = tasks.fail(context, "TaskBoard 存在不可重试失败", clock.instant());
            notifyFailure(failed, "TaskBoard 存在不可重试失败");
            return Flux.empty();
        }
        var paused =
                tasks.pause(
                        context.tenantId(),
                        context.taskId(),
                        context.lease(),
                        "TaskBoard 无可运行节点",
                        clock.instant());
        notifyStatus(paused, "任务因 DAG 无可运行节点暂停", "dag-blocked");
        return Flux.empty();
    }

    private Flux<ExecutionEvent> withHeartbeat(
            Flux<ExecutionEvent> execution, TaskId taskId, Lease initialLease) {
        var currentLease = new AtomicReference<>(initialLease);
        var period = leaseTtl.dividedBy(3);
        return execution.publish(
                shared ->
                        Flux.merge(
                                shared,
                                Flux.interval(period)
                                        .doOnNext(
                                                ignored -> {
                                                    var renewed =
                                                            leases.renew(
                                                                            currentLease.get(),
                                                                            leaseTtl)
                                                                    .orElseThrow(
                                                                            () ->
                                                                                    new IllegalStateException(
                                                                                            "conversation lease 续约失败"));
                                                    currentLease.set(renewed);
                                                    tasks.renew(taskId, renewed, clock.instant());
                                                })
                                        .takeUntilOther(shared.ignoreElements())
                                        .thenMany(Flux.<ExecutionEvent>empty())));
    }

    private TaskBoard requireBoard(DelegatedTaskPort.StoredTask stored) {
        return boards.find(stored.task().tenantId(), stored.task().taskId())
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "委托任务缺少持久 TaskBoard: " + stored.task().taskId().value()));
    }

    private void cancelRunningChildren(TenantId tenantId, TaskId taskId) {
        boards.find(tenantId, taskId)
                .ifPresent(
                        board ->
                                board.subTasks().values().stream()
                                        .filter(
                                                subTask ->
                                                        subTask.status()
                                                                == TaskBoard.Status.RUNNING)
                                        .forEach(
                                                subTask ->
                                                        agentExecution
                                                                .cancel(subTask.executionId())
                                                                .subscribe()));
    }

    private void failParent(InvocationContext context, Throwable failure) {
        try {
            var failed =
                    tasks.failOrRetry(
                            context,
                            Objects.requireNonNullElse(failure.getMessage(), "执行失败"),
                            isTransient(failure),
                            clock.instant());
            notifyFailure(failed, failure.getMessage());
        } catch (DelegatedTaskPort.StaleExecutionException ignored) {
            // 新 fencing owner 已接管。
        }
    }

    private void notifyDeadlinePauseIfNeeded(TenantId tenantId, TaskId taskId) {
        tasks.find(tenantId, taskId)
                .map(DelegatedTaskPort.StoredTask::task)
                .filter(latest -> latest.status() == Status.PAUSED)
                .ifPresent(
                        latest -> notifyStatus(latest, "委托任务已到 deadline 并暂停", "deadline-paused"));
    }

    private void notifyBudgetIfNeeded(InvocationContext context) {
        var task =
                tasks.find(context.tenantId(), context.taskId())
                        .map(DelegatedTaskPort.StoredTask::task)
                        .orElse(null);
        if (task == null
                || !task.contract()
                        .notificationPolicy()
                        .triggers()
                        .contains(NotificationTrigger.BUDGET_WARNING)) return;
        var ratio = task.contract().notificationPolicy().budgetWarningRatio();
        var usage = task.budgetUsage();
        var budget = task.contract().budget();
        var warning =
                (double) usage.modelTokens() / budget.modelTokens() >= ratio
                        || (double) usage.toolUnits() / budget.toolUnits() >= ratio
                        || usage.credits()
                                        .divide(budget.credits(), 4, java.math.RoundingMode.HALF_UP)
                                        .doubleValue()
                                >= ratio;
        if (warning) {
            notify(context, Type.BUDGET_WARNING, "委托预算接近上限", "budget-warning", Map.of());
        }
    }

    private void notifyFailure(DelegatedTask task, String failure) {
        if (task.contract()
                .notificationPolicy()
                .triggers()
                .contains(NotificationTrigger.CONSECUTIVE_FAILURE)) {
            notifications.notify(
                    new Notification(
                            notificationId(task.taskId(), "failure-" + task.consecutiveFailures()),
                            task.tenantId(),
                            task.userId(),
                            task.taskId(),
                            Type.CONSECUTIVE_FAILURE,
                            "委托任务连续失败 " + task.consecutiveFailures() + " 次",
                            Map.of("failure", Objects.requireNonNullElse(failure, "执行失败")),
                            clock.instant()));
        }
    }

    private void notifyStatus(DelegatedTask task, String summary, String suffix) {
        var policy = task.contract().notificationPolicy().triggers();
        var completed = task.status() == Status.COMPLETED;
        if ((!completed || !policy.contains(NotificationTrigger.COMPLETED))
                && !policy.contains(NotificationTrigger.STATUS_CHANGED)) return;
        notifications.notify(
                new Notification(
                        notificationId(task.taskId(), suffix),
                        task.tenantId(),
                        task.userId(),
                        task.taskId(),
                        completed ? Type.COMPLETED : Type.STATUS_CHANGED,
                        summary,
                        Map.of("status", task.status().name()),
                        clock.instant()));
    }

    private void notify(
            InvocationContext context,
            Type type,
            String summary,
            String suffix,
            Map<String, Object> details) {
        var trigger =
                switch (type) {
                    case AUTHORIZATION_GAP -> NotificationTrigger.AUTHORIZATION_GAP;
                    case COMPLETED -> NotificationTrigger.COMPLETED;
                    case BUDGET_WARNING -> NotificationTrigger.BUDGET_WARNING;
                    case CONSECUTIVE_FAILURE -> NotificationTrigger.CONSECUTIVE_FAILURE;
                    case STATUS_CHANGED -> NotificationTrigger.STATUS_CHANGED;
                };
        if (!context.executionContract().notificationPolicy().triggers().contains(trigger)) return;
        notifications.notify(
                new Notification(
                        notificationId(context.taskId(), suffix),
                        context.tenantId(),
                        context.userId(),
                        context.taskId(),
                        type,
                        summary,
                        details,
                        clock.instant()));
    }

    private Mono<ExecutionEvent> appendInputEvent(
            AssistantCommand command, ExecutionInput input, Lease lease) {
        var type =
                switch (input.kind()) {
                    case CANCEL -> ExecutionEventType.INPUT_CANCELED;
                    case MODIFY -> ExecutionEventType.INPUT_MODIFIED;
                    case SUPPLEMENT -> ExecutionEventType.INPUT_SUPPLEMENTED;
                    case UNRELATED -> ExecutionEventType.INPUT_UNRELATED;
                };
        var status =
                switch (input.kind()) {
                    case CANCEL -> ExecutionEventStatus.CANCELED;
                    case MODIFY, SUPPLEMENT -> ExecutionEventStatus.PLANNING;
                    case UNRELATED -> ExecutionEventStatus.RUNNING;
                };
        var payload = new LinkedHashMap<String, Object>();
        payload.put("inputId", input.inputId());
        payload.put("kind", input.kind().name());
        payload.put("content", input.content());
        var event =
                new ExecutionEvent(
                        new EventId("input-" + input.inputId()),
                        command.tenantId(),
                        command.conversationId(),
                        command.sessionId(),
                        command.taskId(),
                        command.executionId(),
                        command.runId(),
                        command.parentExecutionId(),
                        0,
                        type,
                        status,
                        command.controlMode(),
                        OwnerType.HUMAN,
                        command.assistantId(),
                        null,
                        command.userId(),
                        command.correlationId(),
                        command.causationId(),
                        command.idempotencyKey(),
                        new ExecutionEventPayload(payload),
                        input.receivedAt());
        return events.append(event, lease);
    }

    private InvocationContext context(AssistantCommand command) {
        return new InvocationContext(
                command.tenantId(),
                command.userId(),
                command.assistantId(),
                command.conversationId(),
                command.sessionId(),
                command.taskId(),
                command.executionId(),
                command.runId(),
                command.parentExecutionId(),
                command.correlationId(),
                command.causationId(),
                command.idempotencyKey(),
                command.controlMode(),
                command.executionContract(),
                command.lease(),
                new ToolAuthorizationContext(Map.of()));
    }

    private DelegatedTaskPort.StoredTask requireOwned(
            TenantId tenantId, UserId userId, TaskId taskId) {
        var stored =
                tasks.find(tenantId, taskId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("委托任务不存在: " + taskId.value()));
        if (!stored.task().userId().equals(userId)) {
            throw new IllegalArgumentException("委托任务不属于当前用户");
        }
        return stored;
    }

    private LeaseUse diagnosticOrAcquiredLease(DelegatedTask task, String owner) {
        var current = leases.inspect(task.tenantId(), task.conversationId());
        if (current.isPresent()) return new LeaseUse(current.get(), false);
        return new LeaseUse(
                leases.acquire(task.tenantId(), task.conversationId(), owner, leaseTtl)
                        .orElseThrow(() -> new IllegalStateException("无法取得 conversation 输入租约")),
                true);
    }

    private void releaseOnce(Lease lease, AtomicBoolean released) {
        if (released.compareAndSet(false, true)) leases.release(lease);
    }

    private static boolean isTransient(Throwable failure) {
        return !(failure instanceof IllegalArgumentException
                || failure instanceof DelegatedTaskPort.BudgetExceededException);
    }

    private static boolean isTransientMessage(String failure) {
        var normalized = failure.toLowerCase(java.util.Locale.ROOT);
        return !(normalized.contains("illegalargument")
                || normalized.contains("未允许")
                || normalized.contains("禁止")
                || normalized.contains("缺少强制"));
    }

    private static String notificationId(TaskId taskId, String suffix) {
        var source = taskId.value() + '|' + suffix;
        return "notification-" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private record LeaseUse(Lease lease, boolean acquired) {}
}
