package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.xuejiai.aaf.framework.intelligent.assistant.model.ClarificationRequest;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CoordinationPlan;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CoordinationPlan.ExecutorAssignment;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DecompositionBudget;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.BudgetUsage;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.Owner;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.OwnerKind;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.Source;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.Status;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionContract.NotificationTrigger;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;
import com.xuejiai.aaf.framework.intelligent.assistant.model.IterationEvaluation;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard.SubTask;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskModelSelection;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.ClarificationRequestTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.InputTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.IterationEvaluationTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.ParentFailureTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort.Lease;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskDispatchPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.NotificationPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.NotificationPort.Notification;
import com.xuejiai.aaf.framework.intelligent.assistant.port.NotificationPort.Type;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskBoardPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskTransitionPort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** DELEGATED 唯一应用入口；任务、DAG 与预算事实均在 PostgreSQL。 */
@Slf4j
public final class DelegatedTaskCoordinator {
    private static final int MAX_COORDINATION_PLAN_CHARS = 12_000;
    private static final JsonMapper COORDINATION_PLAN_JSON =
            JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private static final String DELEGATED_TASK_TYPE = "delegated-task";
    private static final Set<String> CONVERSATION_ALLOWED_ACTIONS =
            Set.of("knowledge.search", "content.generate");

    private final DelegatedTaskPort tasks;
    private final TaskTransitionPort transitions;
    private final TaskIngress taskIngress;
    private final TaskBoardPort boards;
    private final ConversationLeasePort leases;
    private final AssistantCommandPort commands;
    private final AgentExecutionPort agentExecution;
    private final NotificationPort notifications;
    private final DelegatedTaskDispatchPort dispatch;
    private final AgentTaskRuntime agentTaskRuntime;
    private final DecompositionBudget decompositionBudget;
    private final Clock clock;
    private final Duration leaseTtl;

    public DelegatedTaskCoordinator(
            DelegatedTaskPort tasks,
            TaskTransitionPort transitions,
            TaskIngress taskIngress,
            TaskBoardPort boards,
            ConversationLeasePort leases,
            AssistantCommandPort commands,
            AgentExecutionPort agentExecution,
            NotificationPort notifications,
            DelegatedTaskDispatchPort dispatch,
            AgentTaskRuntime agentTaskRuntime,
            DecompositionBudget decompositionBudget,
            Clock clock,
            Duration leaseTtl) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
        this.transitions = Objects.requireNonNull(transitions, "transitions 不能为空");
        this.taskIngress = Objects.requireNonNull(taskIngress, "taskIngress 不能为空");
        this.boards = Objects.requireNonNull(boards, "boards 不能为空");
        this.leases = Objects.requireNonNull(leases, "leases 不能为空");
        this.commands = Objects.requireNonNull(commands, "commands 不能为空");
        this.agentExecution = Objects.requireNonNull(agentExecution, "agentExecution 不能为空");
        this.notifications = Objects.requireNonNull(notifications, "notifications 不能为空");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch 不能为空");
        this.agentTaskRuntime = Objects.requireNonNull(agentTaskRuntime, "agentTaskRuntime 不能为空");
        this.decompositionBudget =
                Objects.requireNonNull(decompositionBudget, "decompositionBudget 不能为空");
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
                0,
                true);
    }

    public DelegatedTask submit(AssistantCommand command, TaskBoard board) {
        return submit(command, board, Source.AUTOMATION, 0, true);
    }

    /** 创建持久任务后由当前请求持有首个调度租约，供 Headless SSE 连续投影。 */
    public Flux<ExecutionEvent> submitAndDispatch(
            AssistantCommand command, TaskBoard board, String workerId) {
        submit(command, board, Source.CONVERSATION, 0, false);
        return dispatch(command.tenantId(), command.taskId(), workerId);
    }

    private DelegatedTask submit(
            AssistantCommand command,
            TaskBoard board,
            Source source,
            int priority,
            boolean signalDispatch) {
        Objects.requireNonNull(command, "command 不能为空");
        Objects.requireNonNull(board, "board 不能为空");
        Objects.requireNonNull(source, "source 不能为空");
        if (!persistentControlMode(command.controlMode())
                || command.executionContract() == null
                || command.operation() != AssistantCommand.Operation.START) {
            throw new IllegalArgumentException("持久任务必须由合法 control mode、完整合同的 START 命令创建");
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
        var stored =
                transitions.create(
                        new TaskTransition(task, command, board, List.of(submittedEvent(command))));
        notifyStatus(stored, "委托任务已进入持久调度队列", "submitted");
        if (signalDispatch) {
            dispatch.signal(command.tenantId(), command.taskId());
        }
        return stored;
    }

    public Flux<ExecutionEvent> dispatch(TenantId tenantId, TaskId taskId, String workerId) {
        var stored =
                tasks.find(tenantId, taskId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("委托任务不存在: " + taskId.value()));
        var lease =
                leases.acquire(tenantId, stored.task().conversationId(), workerId, leaseTtl)
                        .orElse(null);
        if (lease == null) return Flux.empty();
        var now = clock.instant();
        var inputCommand = stored.command().withLease(lease, now);
        transitions.consumeInputs(new InputTransition(context(inputCommand), now));
        stored = tasks.find(tenantId, taskId).orElseThrow();
        if (stored.task().terminal()
                || stored.task().status() == Status.AWAITING_CLARIFICATION
                || stored.task().status() == Status.AWAITING_AUTHORIZATION
                || stored.task().status() == Status.PAUSED) {
            leases.release(lease);
            return Flux.empty();
        }
        var claimed = tasks.claim(tenantId, taskId, lease, clock.instant()).orElse(null);
        if (claimed == null) {
            leases.release(lease);
            notifyDeadlinePauseIfNeeded(tenantId, taskId);
            return Flux.empty();
        }
        var command = claimed.command().withLease(lease, clock.instant());
        var parentContext = context(command);
        var board = requireBoard(claimed);
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
        return taskIngress
                .accept(input)
                .map(
                        ignored ->
                                requireOwned(input.tenantId(), input.userId(), input.taskId())
                                        .task());
    }

    public int recoverAndDispatch(String workerId, int limit) {
        var recovered = tasks.recoverExpired(clock.instant());
        try {
            transitions.publishOutbox(Math.min(limit, 100));
        } catch (TaskTransitionPort.OutboxRelayException ignored) {
            // relay 已按安全失败类型计数；不得用异常正文污染恢复日志。
        }
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
                                            executeSubTask(
                                                    parentCommand,
                                                    parentContext,
                                                    claim.board(),
                                                    subTask),
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
            AssistantCommand parentCommand,
            InvocationContext parentContext,
            TaskBoard board,
            SubTask subTask) {
        var childCommand =
                parentCommand.forSubTask(
                        subTask,
                        board.resolveInput(subTask),
                        parentCommand.lease(),
                        clock.instant());
        var result = new AtomicReference<>("");
        var observedEvents = new java.util.ArrayList<ExecutionEvent>();
        var failure = new AtomicReference<String>();
        var clarification = new AtomicReference<ClarificationRequest>();
        var completed = new AtomicBoolean();
        var awaitingAuthorization = new AtomicBoolean();
        log.debug(
                "[Assistant协调] 子 Agent 即将执行：taskId={}，executionId={}，agentKind={}，agentKey={}，模型模式={}",
                parentContext.taskId().value(),
                subTask.executionId().value(),
                subTask.kind(),
                subTask.subTaskId(),
                subTask.modelSelection().mode());
        return commands.execute(childCommand)
                .doOnNext(
                        event -> {
                            observedEvents.add(event);
                            notifyBudgetIfNeeded(parentContext);
                            if (event.type() == ExecutionEventType.MESSAGE_COMPLETED) {
                                var text = event.payload().values().get("text");
                                if (text != null) result.set(text.toString());
                            }
                            if (event.type() == ExecutionEventType.EXECUTION_COMPLETED) {
                                completed.set(true);
                            }
                            if (event.status() == ExecutionEventStatus.AWAITING_AUTHORIZATION) {
                                awaitingAuthorization.set(true);
                                notify(
                                        parentContext,
                                        Type.AUTHORIZATION_GAP,
                                        "任务等待授权",
                                        event.eventId().value(),
                                        Map.of("eventType", event.type().name()));
                            }
                            if (event.status() == ExecutionEventStatus.AWAITING_CLARIFICATION
                                    && clarification.get() == null) {
                                clarification.set(
                                        decodeClarificationRequest(
                                                parentContext, subTask, event, clock.instant()));
                            }
                            if (event.type() == ExecutionEventType.EXECUTION_FAILED
                                    || event.type() == ExecutionEventType.COMMAND_REJECTED
                                    || event.status() == ExecutionEventStatus.FAILED
                                    || event.status() == ExecutionEventStatus.REJECTED) {
                                failure.compareAndSet(null, "子 Agent 执行未通过状态校验");
                            }
                        })
                .takeUntil(event -> awaitingAuthorization.get() || clarification.get() != null)
                .onErrorResume(
                        error -> {
                            failure.compareAndSet(null, "子任务执行异常");
                            return Flux.empty();
                        })
                .concatWith(
                        Flux.defer(
                                () -> {
                                    if (awaitingAuthorization.get()) {
                                        return Flux.empty();
                                    }
                                    if (clarification.get() != null) {
                                        var request = clarification.get();
                                        var event =
                                                clarificationRequestedEvent(
                                                        parentContext, request, clock.instant());
                                        transitions.requestClarification(
                                                new ClarificationRequestTransition(
                                                        parentContext, request, event));
                                        return Flux.just(event);
                                    }
                                    if (failure.get() != null
                                            || !completed.get()
                                            || (subTask.kind() == SubTask.Kind.EXECUTOR
                                                    && !completionEvidenceSatisfied(
                                                            parentCommand, observedEvents))) {
                                        boards.failSubTask(
                                                parentContext.tenantId(),
                                                parentContext.taskId(),
                                                subTask.subTaskId(),
                                                Objects.requireNonNullElse(
                                                        failure.get(), "子任务未产生完整终态"),
                                                failure.get() == null
                                                        || isTransientMessage(failure.get()),
                                                parentContext.lease());
                                    } else if (subTask.kind() == SubTask.Kind.COORDINATOR) {
                                        var plan =
                                                decodeAndValidatePlan(
                                                        parentCommand, board, result.get());
                                        boards.applyCoordinationPlan(
                                                parentContext.tenantId(),
                                                parentContext.taskId(),
                                                plan,
                                                parentContext.lease());
                                        log.debug(
                                                "[Assistant协调] 协调计划已冻结：taskId={}，coordinatorExecutionId={}，执行者数={}，并行度={}",
                                                parentContext.taskId().value(),
                                                subTask.executionId().value(),
                                                plan.executors().size(),
                                                plan.maxParallelism());
                                    } else if (subTask.kind() == SubTask.Kind.EVALUATOR) {
                                        var evaluation = decodeIterationEvaluation(result.get());
                                        var at = clock.instant();
                                        var event =
                                                iterationEvent(
                                                        parentContext,
                                                        board.goal().iteration(),
                                                        evaluation,
                                                        at);
                                        var committed =
                                                transitions.evaluateIteration(
                                                        new IterationEvaluationTransition(
                                                                parentContext,
                                                                subTask.subTaskId(),
                                                                evaluation,
                                                                event,
                                                                at));
                                        return Flux.just(committed.event());
                                    } else {
                                        boards.completeSubTask(
                                                parentContext.tenantId(),
                                                parentContext.taskId(),
                                                subTask.subTaskId(),
                                                result.get(),
                                                parentContext.lease());
                                        log.debug(
                                                "[Assistant协调] 执行子 Agent 已完成：taskId={}，executionId={}，agentKey={}",
                                                parentContext.taskId().value(),
                                                subTask.executionId().value(),
                                                subTask.subTaskId());
                                    }
                                    return Flux.empty();
                                }))
                .filter(event -> visibleToTaskConsumer(subTask, event));
    }

    private Flux<ExecutionEvent> finalizeBoard(InvocationContext context, TaskBoard board) {
        var consumed = transitions.consumeInputs(new InputTransition(context, clock.instant()));
        if (consumed.isPresent() && consumed.orElseThrow().task().status() != Status.RUNNING) {
            return Flux.empty();
        }
        var latestBoard = boards.find(context.tenantId(), context.taskId()).orElse(board);
        var at = clock.instant();
        if (latestBoard.completed()) {
            var results = new LinkedHashMap<String, Object>();
            latestBoard.subTasks().forEach((id, subTask) -> results.put(id, subTask.result()));
            var executorCount =
                    latestBoard.subTasks().values().stream()
                            .filter(subTask -> subTask.kind() == SubTask.Kind.EXECUTOR)
                            .count();
            var committed =
                    transitions.commitParent(
                            new TaskTransition.ParentStateTransition(
                                    context,
                                    latestBoard,
                                    Status.COMPLETED,
                                    results,
                                    null,
                                    List.of(
                                            parentEvent(
                                                    context,
                                                    ExecutionEventType.MESSAGE_COMPLETED,
                                                    ExecutionEventStatus.RUNNING,
                                                    Map.of(
                                                            "text",
                                                            latestBoard.aggregateResults(),
                                                            "agentKind",
                                                            "EXECUTOR"),
                                                    at),
                                            parentEvent(
                                                    context,
                                                    ExecutionEventType.EXECUTION_COMPLETED,
                                                    ExecutionEventStatus.COMPLETED,
                                                    Map.of(
                                                            "agentKind",
                                                            "ASSISTANT",
                                                            "subtaskCount",
                                                            Math.toIntExact(executorCount)),
                                                    at)),
                                    at));
            notifyStatus(committed.task(), "委托任务完成", "completed");
            return Flux.fromIterable(committed.events());
        }
        if (latestBoard.hasTerminalFailure()) {
            var reason = "TaskBoard 存在不可重试失败";
            var committed =
                    transitions.commitParent(
                            new TaskTransition.ParentStateTransition(
                                    context,
                                    latestBoard,
                                    Status.FAILED,
                                    Map.of(),
                                    reason,
                                    List.of(
                                            parentEvent(
                                                    context,
                                                    ExecutionEventType.EXECUTION_FAILED,
                                                    ExecutionEventStatus.FAILED,
                                                    Map.of("agentKind", "ASSISTANT"),
                                                    at)),
                                    at));
            notifyFailure(committed.task(), reason);
            return Flux.fromIterable(committed.events());
        }
        var reason = "TaskBoard 无可运行节点";
        var committed =
                transitions.commitParent(
                        new TaskTransition.ParentStateTransition(
                                context,
                                latestBoard,
                                Status.PAUSED,
                                Map.of(),
                                reason,
                                List.of(
                                        parentEvent(
                                                context,
                                                ExecutionEventType.EXECUTION_PAUSED,
                                                ExecutionEventStatus.PAUSED,
                                                Map.of("agentKind", "ASSISTANT"),
                                                at)),
                                at));
        notifyStatus(committed.task(), "任务因 DAG 无可运行节点暂停", "dag-blocked");
        return Flux.fromIterable(committed.events());
    }

    private static ExecutionEvent submittedEvent(AssistantCommand command) {
        return new ExecutionEvent(
                new EventId("task-submitted-" + command.taskId().value()),
                command.tenantId(),
                command.conversationId(),
                command.sessionId(),
                command.taskId(),
                command.executionId(),
                command.runId(),
                command.parentExecutionId(),
                1,
                ExecutionEventType.TASK_STATUS_CHANGED,
                ExecutionEventStatus.PLANNING,
                command.controlMode(),
                OwnerType.ASSISTANT,
                command.assistantId(),
                null,
                command.userId(),
                command.correlationId(),
                command.causationId(),
                command.idempotencyKey(),
                new ExecutionEventPayload(Map.of("status", Status.PENDING.name())),
                command.requestedAt());
    }

    private static ExecutionEvent parentEvent(
            InvocationContext context,
            ExecutionEventType type,
            ExecutionEventStatus status,
            Map<String, Object> payload,
            Instant at) {
        var eventId =
                UUID.nameUUIDFromBytes(
                                (context.taskId().value()
                                                + '|'
                                                + context.executionId().value()
                                                + '|'
                                                + type.name())
                                        .getBytes(StandardCharsets.UTF_8))
                        .toString();
        return new ExecutionEvent(
                new EventId(eventId),
                context.tenantId(),
                context.conversationId(),
                context.sessionId(),
                context.taskId(),
                context.executionId(),
                context.runId(),
                context.parentExecutionId(),
                1,
                type,
                status,
                context.controlMode(),
                OwnerType.ASSISTANT,
                context.assistantId(),
                null,
                context.userId(),
                context.correlationId(),
                context.causationId(),
                context.idempotencyKey(),
                new ExecutionEventPayload(payload),
                at);
    }

    private static boolean visibleToTaskConsumer(SubTask subTask, ExecutionEvent event) {
        if (event.type() == ExecutionEventType.CLARIFICATION_REQUESTED
                || event.type() == ExecutionEventType.ITERATION_EVALUATED
                || event.type() == ExecutionEventType.ITERATION_STOPPED) {
            return true;
        }
        if (subTask.kind() != SubTask.Kind.EXECUTOR) {
            return false;
        }
        return switch (event.type()) {
            case MESSAGE_COMPLETED,
                    RUN_COMPLETED,
                    EXECUTION_COMPLETED,
                    EXECUTION_FAILED,
                    EXECUTION_CANCELED,
                    EXECUTION_PAUSED,
                    COMMAND_REJECTED ->
                    false;
            default -> true;
        };
    }

    private static IterationEvaluation decodeIterationEvaluation(String output) {
        if (output == null || output.isBlank() || output.length() > 2_000) {
            throw new IllegalArgumentException("evaluator 未返回合法大小的决策");
        }
        final JsonNode root;
        try (var parser = COORDINATION_PLAN_JSON.createParser(output)) {
            root = COORDINATION_PLAN_JSON.readTree(parser);
            if (root == null || parser.nextToken() != null) {
                throw new IllegalArgumentException("evaluator 输出必须是单一 JSON 对象");
            }
        } catch (Exception failure) {
            throw new IllegalArgumentException("evaluator 输出不是严格 JSON 决策");
        }
        requireObject(root, "IterationEvaluation");
        requireFields(root, Set.of("decision", "reason"), Set.of("decision", "reason"));
        final IterationEvaluation.Decision decision;
        try {
            decision = IterationEvaluation.Decision.valueOf(requiredText(root, "decision"));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("evaluator decision 只允许 CONTINUE/COMPLETE/BLOCKED");
        }
        return new IterationEvaluation(decision, requiredText(root, "reason"));
    }

    private static ClarificationRequest decodeClarificationRequest(
            InvocationContext parentContext,
            SubTask subTask,
            ExecutionEvent source,
            Instant createdAt) {
        var raw = source.payload().values().get("clarificationRequest");
        if (raw == null) {
            throw new IllegalArgumentException("AWAITING_CLARIFICATION 缺少 clarificationRequest");
        }
        var root = COORDINATION_PLAN_JSON.valueToTree(raw);
        requireObject(root, "clarificationRequest");
        requireFields(
                root,
                Set.of("requiredFields", "questions", "deadline"),
                Set.of("requiredFields", "questions", "deadline"));
        var requiredFields = stringList(root.get("requiredFields"), "requiredFields");
        var questionNodes = root.get("questions");
        if (questionNodes == null || !questionNodes.isArray()) {
            throw new IllegalArgumentException("clarification questions 必须是数组");
        }
        var questions = new java.util.ArrayList<ClarificationRequest.Question>();
        for (var questionNode : questionNodes) {
            requireObject(questionNode, "clarification question");
            requireFields(
                    questionNode,
                    Set.of("field", "question", "options"),
                    Set.of("field", "question", "options"));
            questions.add(
                    new ClarificationRequest.Question(
                            requiredText(questionNode, "field"),
                            requiredText(questionNode, "question"),
                            stringList(questionNode.get("options"), "options")));
        }
        final Instant deadline;
        try {
            deadline = Instant.parse(requiredText(root, "deadline"));
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("clarification deadline 必须是 ISO-8601 Instant");
        }
        if (deadline.isAfter(parentContext.executionContract().deadline())) {
            throw new IllegalArgumentException("clarification deadline 不能晚于任务 deadline");
        }
        var requestId =
                "clarification-"
                        + UUID.nameUUIDFromBytes(
                                (parentContext.taskId().value()
                                                + '|'
                                                + subTask.executionId().value()
                                                + '|'
                                                + subTask.subTaskId())
                                        .getBytes(StandardCharsets.UTF_8));
        return new ClarificationRequest(
                requestId,
                parentContext.taskId(),
                subTask.executionId(),
                subTask.subTaskId(),
                requiredFields,
                questions,
                deadline,
                ClarificationRequest.Status.PENDING,
                Map.of(),
                createdAt,
                null,
                null);
    }

    private static ExecutionEvent clarificationRequestedEvent(
            InvocationContext context, ClarificationRequest request, Instant at) {
        var questions =
                request.questions().stream()
                        .map(
                                question ->
                                        Map.<String, Object>of(
                                                "field",
                                                question.field(),
                                                "question",
                                                question.question(),
                                                "options",
                                                question.options()))
                        .toList();
        return new ExecutionEvent(
                new EventId("clarification-requested-" + request.requestId()),
                context.tenantId(),
                context.conversationId(),
                context.sessionId(),
                context.taskId(),
                context.executionId(),
                context.runId(),
                context.parentExecutionId(),
                1,
                ExecutionEventType.CLARIFICATION_REQUESTED,
                ExecutionEventStatus.AWAITING_CLARIFICATION,
                context.controlMode(),
                OwnerType.ASSISTANT,
                context.assistantId(),
                null,
                context.userId(),
                context.correlationId(),
                context.causationId(),
                context.idempotencyKey(),
                new ExecutionEventPayload(
                        Map.of(
                                "requestId", request.requestId(),
                                "subTaskId", request.subTaskId(),
                                "requiredFields", request.requiredFields(),
                                "questions", questions,
                                "deadline", request.deadline().toString())),
                at);
    }

    private static ExecutionEvent iterationEvent(
            InvocationContext context,
            TaskBoard.IterationState iteration,
            IterationEvaluation evaluation,
            Instant at) {
        var eventId =
                "iteration-evaluated-"
                        + context.taskId().value()
                        + '-'
                        + iteration.group().groupId()
                        + '-'
                        + iteration.currentIteration();
        return new ExecutionEvent(
                new EventId(eventId),
                context.tenantId(),
                context.conversationId(),
                context.sessionId(),
                context.taskId(),
                context.executionId(),
                context.runId(),
                context.parentExecutionId(),
                1,
                ExecutionEventType.ITERATION_EVALUATED,
                ExecutionEventStatus.RUNNING,
                context.controlMode(),
                OwnerType.ASSISTANT,
                context.assistantId(),
                null,
                context.userId(),
                context.correlationId(),
                context.causationId(),
                context.idempotencyKey(),
                new ExecutionEventPayload(
                        Map.of(
                                "groupId", iteration.group().groupId(),
                                "iteration", iteration.currentIteration(),
                                "decision", evaluation.decision().name())),
                at);
    }

    private CoordinationPlan decodeAndValidatePlan(
            AssistantCommand command, TaskBoard board, String output) {
        if (output == null || output.isBlank() || output.length() > MAX_COORDINATION_PLAN_CHARS) {
            throw new IllegalArgumentException("协调者未返回合法大小的 CoordinationPlan");
        }
        final JsonNode root;
        try (var parser = COORDINATION_PLAN_JSON.createParser(output)) {
            root = COORDINATION_PLAN_JSON.readTree(parser);
            if (root == null || parser.nextToken() != null) {
                throw new IllegalArgumentException("协调者输出必须是单一 JSON 对象");
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("协调者输出不是严格 JSON 计划");
        }
        requireObject(root, "CoordinationPlan");
        requireFields(
                root,
                Set.of("goal", "executors", "aggregationContract"),
                Set.of(
                        "goal",
                        "executors",
                        "maxParallelism",
                        "aggregationContract",
                        "iterationGroup"));
        var executors = root.get("executors");
        if (!executors.isArray() || executors.isEmpty() || executors.size() > 8) {
            throw new IllegalArgumentException("协调计划必须包含 1..8 个执行者");
        }
        var teamTargets =
                board.subTasks().values().stream()
                        .filter(subTask -> subTask.kind() == SubTask.Kind.EXECUTOR)
                        .filter(subTask -> subTask.assistantTarget() != null)
                        .collect(
                                java.util.stream.Collectors.toUnmodifiableMap(
                                        SubTask::subTaskId, SubTask::assistantTarget));
        var coordinator = board.subTasks().get("coordinator");
        var teamBoard =
                coordinator != null
                        && coordinator.assistantTarget() != null
                        && !teamTargets.isEmpty();
        var route = command.invocationProfile().executionIntent().resolvedRoute();
        if (!teamBoard && route == null) {
            throw new IllegalStateException("协调计划只能用于已冻结 FIXED Route");
        }
        var assignments = new java.util.ArrayList<ExecutorAssignment>();
        for (var node : executors) {
            requireObject(node, "executor");
            requireFields(
                    node,
                    Set.of("subTaskId", "description", "roleKey", "skillKey", "modelMode"),
                    Set.of(
                            "subTaskId",
                            "description",
                            "dependsOn",
                            "inputBindings",
                            "roleKey",
                            "skillKey",
                            "modelMode",
                            "maxAttempts"));
            var subTaskId = requiredText(node, "subTaskId");
            var roleKey = requiredText(node, "roleKey");
            var skillKey = requiredText(node, "skillKey");
            if (teamBoard) {
                var target = teamTargets.get(subTaskId);
                if (target == null
                        || !target.roleKey().equals(roleKey)
                        || !target.skillKey().equals(skillKey)) {
                    throw new IllegalArgumentException("Team 协调计划不能改变或跳过冻结 Worker");
                }
            } else if (!route.roleKey().equals(roleKey) || !route.skillKey().equals(skillKey)) {
                throw new IllegalArgumentException("协调者不能更改已冻结的 Role 或 Skill");
            }
            var modelMode = requiredText(node, "modelMode");
            final TaskModelSelection modelSelection;
            if (command.taskModelSelection().mode() == TaskModelSelection.Mode.AUTO
                    && TaskModelSelection.Mode.AUTO.name().equals(modelMode)) {
                modelSelection = TaskModelSelection.auto();
            } else if (command.taskModelSelection().mode() == TaskModelSelection.Mode.EXPLICIT
                    && TaskModelSelection.Mode.EXPLICIT.name().equals(modelMode)) {
                modelSelection =
                        TaskModelSelection.explicit(command.taskModelSelection().modelId());
            } else {
                throw new IllegalArgumentException("协调者不能改变用户冻结的模型策略");
            }
            var dependsOn = stringSet(node.get("dependsOn"));
            if (dependsOn.isEmpty()) dependsOn = Set.of("coordinator");
            assignments.add(
                    new ExecutorAssignment(
                            subTaskId,
                            requiredText(node, "description"),
                            dependsOn,
                            inputBindings(node.get("inputBindings")),
                            roleKey,
                            skillKey,
                            modelSelection,
                            optionalPositive(node, "maxAttempts", 3)));
        }
        if (teamBoard) {
            var plannedWorkerIds =
                    assignments.stream()
                            .map(ExecutorAssignment::subTaskId)
                            .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (!plannedWorkerIds.equals(teamTargets.keySet())) {
                throw new IllegalArgumentException("Team 协调计划必须且只能覆盖全部冻结 Worker");
            }
        }
        var aggregation = aggregationContract(root.get("aggregationContract"));
        var iterationGroup = iterationGroup(root.get("iterationGroup"));
        var maxParallelism = optionalPositive(root, "maxParallelism", 1);
        var effectiveBudget =
                teamBoard
                        ? decompositionBudget.effectiveForFixedTeam(teamTargets.size())
                        : decompositionBudget;
        effectiveBudget.requireWithin(
                assignments.size(),
                maxParallelism,
                iterationGroup == null ? 1 : iterationGroup.maxIterations());
        return new CoordinationPlan(
                requiredText(root, "goal"),
                maxParallelism,
                aggregation,
                assignments,
                iterationGroup);
    }

    private static CoordinationPlan.IterationGroup iterationGroup(JsonNode node) {
        if (node == null || node.isNull()) return null;
        requireObject(node, "iterationGroup");
        requireFields(
                node,
                Set.of("groupId", "memberSubTaskIds", "evaluatorSubTaskId", "maxIterations"),
                Set.of("groupId", "memberSubTaskIds", "evaluatorSubTaskId", "maxIterations"));
        return new CoordinationPlan.IterationGroup(
                requiredText(node, "groupId"),
                stringList(node.get("memberSubTaskIds"), "memberSubTaskIds"),
                requiredText(node, "evaluatorSubTaskId"),
                optionalPositive(node, "maxIterations", 1));
    }

    private static CoordinationPlan.AggregationContract aggregationContract(JsonNode node) {
        requireObject(node, "aggregationContract");
        requireFields(
                node,
                Set.of("kind", "executorOrder"),
                Set.of("kind", "executorOrder", "separator"));
        final CoordinationPlan.AggregationContract.Kind kind;
        try {
            kind = CoordinationPlan.AggregationContract.Kind.valueOf(requiredText(node, "kind"));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("不支持的 AggregationContract.kind");
        }
        var order = stringList(node.get("executorOrder"), "executorOrder");
        var separatorNode = node.get("separator");
        var separator = separatorNode == null ? "" : separatorNode.asString();
        return new CoordinationPlan.AggregationContract(kind, order, separator);
    }

    private static Map<String, CoordinationPlan.InputBinding> inputBindings(JsonNode node) {
        if (node == null || node.isNull()) return Map.of();
        requireObject(node, "inputBindings");
        var bindings = new LinkedHashMap<String, CoordinationPlan.InputBinding>();
        node.propertyNames()
                .forEach(
                        name -> {
                            var source = node.get(name);
                            if (name.isBlank() || source == null || !source.isString()) {
                                throw new IllegalArgumentException(
                                        "inputBindings 必须是名称到 sourceSubTaskId 的字符串映射");
                            }
                            bindings.put(
                                    name, new CoordinationPlan.InputBinding(source.asString()));
                        });
        return Map.copyOf(bindings);
    }

    private static Set<String> stringSet(JsonNode node) {
        return Set.copyOf(stringList(node, "dependsOn"));
    }

    private static List<String> stringList(JsonNode node, String field) {
        if (node == null || node.isNull()) return List.of();
        if (!node.isArray()) {
            throw new IllegalArgumentException(field + " 必须是字符串数组");
        }
        var values = new java.util.ArrayList<String>();
        for (var value : node) {
            if (!value.isString() || value.asString().isBlank()) {
                throw new IllegalArgumentException(field + " 只能包含非空字符串");
            }
            values.add(value.asString().trim());
        }
        return List.copyOf(values);
    }

    private static void requireObject(JsonNode node, String label) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException(label + " 必须是对象");
    }

    private static void requireFields(JsonNode node, Set<String> required, Set<String> allowed) {
        var names = new java.util.HashSet<String>();
        node.propertyNames().forEach(names::add);
        if (!names.containsAll(required) || !allowed.containsAll(names)) {
            throw new IllegalArgumentException("协调计划字段不符合契约");
        }
    }

    private static String requiredText(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null
                || !value.isString()
                || value.asString().isBlank()
                || value.asString().length() > 4_000) {
            throw new IllegalArgumentException("协调计划字段不合法: " + field);
        }
        return value.asString().trim();
    }

    private static int optionalPositive(JsonNode node, String field, int defaultValue) {
        var value = node.get(field);
        if (value == null) return defaultValue;
        if (!value.canConvertToInt() || value.intValue() < 1) {
            throw new IllegalArgumentException("协调计划字段必须为正整数: " + field);
        }
        return value.intValue();
    }

    private static boolean completionEvidenceSatisfied(
            AssistantCommand command, List<ExecutionEvent> events) {
        var criteria = command.completionCriteria();
        var requiredTypesPresent =
                criteria.requiredEventTypes().stream()
                        .allMatch(
                                required ->
                                        events.stream()
                                                .anyMatch(event -> event.type() == required));
        var requiredPayloadPresent =
                criteria.requiredPayloadValues().entrySet().stream()
                        .allMatch(
                                required ->
                                        events.stream()
                                                .map(
                                                        event ->
                                                                event.payload()
                                                                        .values()
                                                                        .get(required.getKey()))
                                                .anyMatch(
                                                        value ->
                                                                Objects.equals(
                                                                        value,
                                                                        required.getValue())));
        return requiredTypesPresent && requiredPayloadPresent;
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
            var at = clock.instant();
            var transientFailure = isTransient(failure);
            var committed =
                    transitions.failOrRetry(
                            new ParentFailureTransition(
                                    context,
                                    "委托任务执行失败",
                                    transientFailure,
                                    parentEvent(
                                            context,
                                            ExecutionEventType.EXECUTION_FAILED,
                                            ExecutionEventStatus.FAILED,
                                            Map.of(
                                                    "agentKind",
                                                    "ASSISTANT",
                                                    "retryable",
                                                    transientFailure),
                                            at),
                                    at));
            notifyFailure(committed.task(), "委托任务执行失败");
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

    private InvocationContext context(AssistantCommand command) {
        return new InvocationContext(
                command.tenantId(),
                command.userId(),
                null,
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

    private static boolean persistentControlMode(ExecutionEvent.ControlMode mode) {
        return mode == ExecutionEvent.ControlMode.READ_ONLY
                || mode == ExecutionEvent.ControlMode.COLLABORATIVE
                || mode == ExecutionEvent.ControlMode.DELEGATED;
    }

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private record LeaseUse(Lease lease, boolean acquired) {}
}
