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
import com.xuejiai.aaf.framework.intelligent.assistant.model.plan.ExecutorPlan;
import com.xuejiai.aaf.framework.intelligent.assistant.port.plan.ExecutorPlanPort;
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
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskCheckpoint;
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
import com.xuejiai.aaf.framework.intelligent.assistant.port.RecoveryPreflight;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskBoardPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskTransitionPort;
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

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** DELEGATED 唯一应用入口；任务、DAG 与预算事实均在 PostgreSQL。 */
@Slf4j
public final class DelegatedTaskCoordinator {
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
    private final RecoveryPreflight recoveryPreflight;
    private final PlanRequirementPolicy planRequirement;
    private final ExecutorPlanPort plans;
    private final ExecutionEventStorePort eventStore;

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
        this(
                tasks,
                transitions,
                taskIngress,
                boards,
                leases,
                commands,
                agentExecution,
                notifications,
                dispatch,
                agentTaskRuntime,
                decompositionBudget,
                clock,
                leaseTtl,
                new DefaultRecoveryPreflight());
    }

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
            Duration leaseTtl,
            RecoveryPreflight recoveryPreflight) {
        this(
                tasks,
                transitions,
                taskIngress,
                boards,
                leases,
                commands,
                agentExecution,
                notifications,
                dispatch,
                agentTaskRuntime,
                decompositionBudget,
                clock,
                leaseTtl,
                recoveryPreflight,
                (nodeSubTaskId, roleKey, skillKey, coordinatorSuggestsPlan) -> false,
                null,
                null);
    }

    /**
     * 完整构造器：新增 {@code planRequirement}（ADR-006 补充决策二的最终判定）与 {@code plans}（EXECUTOR/COORDINATOR
     * 自行执行时的局部计划状态机端口）。{@code plans} 允许为 {@code null}——尚未接入计划能力的部署（如测试固件）用不到它，
     * {@code planRequirement} 恒返回 {@code false} 时也不会触达该依赖。
     *
     * <p>两个旧构造器都固定传入恒 {@code false} 的 {@code planRequirement} 而非 {@link
     * PlanRequirementPolicy#respectCoordinatorSuggestion()}——若默认尊重协调者建议，未显式传入 {@code plans}
     * 依赖的部署一旦协调者建议规划就会在 {@code executePlannedSubTask} 触发空指针。只有调用本构造器并显式提供 {@code
     * plans} 时才有意义启用非恒 false 的策略。
     */
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
            Duration leaseTtl,
            RecoveryPreflight recoveryPreflight,
            PlanRequirementPolicy planRequirement,
            ExecutorPlanPort plans) {
        this(
                tasks,
                transitions,
                taskIngress,
                boards,
                leases,
                commands,
                agentExecution,
                notifications,
                dispatch,
                agentTaskRuntime,
                decompositionBudget,
                clock,
                leaseTtl,
                recoveryPreflight,
                planRequirement,
                plans,
                null);
    }

    /**
     * 完整构造器（AAF-107 #10705 新增 {@code events}）：{@code events} 用于计划级事件（{@code
     * EXECUTOR_PLAN_*}）的持久化——{@code ExecutorPlan} 是独立聚合根，不经过 {@code TaskTransition}
     * 机制，需要本类直接持有 {@link ExecutionEventStorePort} 自行 {@code append}。允许为 {@code null}，语义与
     * {@code plans} 一致：未接入计划能力的部署用不到它。
     */
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
            Duration leaseTtl,
            RecoveryPreflight recoveryPreflight,
            PlanRequirementPolicy planRequirement,
            ExecutorPlanPort plans,
            ExecutionEventStorePort eventStore) {
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
        this.recoveryPreflight =
                Objects.requireNonNull(recoveryPreflight, "recoveryPreflight 不能为空");
        this.planRequirement = Objects.requireNonNull(planRequirement, "planRequirement 不能为空");
        this.plans = plans;
        this.eventStore = eventStore;
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
                        TaskCheckpoint.empty(),
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
        var now = clock.instant();
        tasks.findDispatchable(now, limit)
                .forEach(
                        stored -> {
                            var preflight = recoveryPreflight.check(stored.task(), now);
                            if (preflight.allowed()) {
                                dispatch.signal(stored.task().tenantId(), stored.task().taskId());
                            }
                            // preflight 拒绝的任务保持原状态，不调度也不强行终结；
                            // 留给下一轮恢复扫描或人工介入处理，不得静默丢弃。
                        });
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

    // 包内可见以便测试直接驱动单个子任务完成判定，不代表对外 API。
    Flux<ExecutionEvent> executeSubTask(
            AssistantCommand parentCommand,
            InvocationContext parentContext,
            TaskBoard board,
            SubTask subTask) {
        if (subTask.requiresPlan()) {
            return executePlannedSubTask(parentCommand, parentContext, board, subTask);
        }
        var childCommand =
                parentCommand.forSubTask(
                        subTask,
                        board.resolveInput(subTask),
                        parentCommand.lease(),
                        clock.instant(),
                        // 交付角色由聚合契约决定，在此处传入——建板信息只有编排层完整
                        board.goal().aggregationContract().kind());
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
                                            || ((subTask.kind() == SubTask.Kind.EXECUTOR
                                                            || subTask.kind()
                                                                    == SubTask.Kind.AGGREGATOR)
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
                                        // 计划已在 submit_coordination_plan 工具调用时同步落地
                                        // （SubmitCoordinationPlanTool.submit → boards.applyCoordinationPlan），
                                        // 这里不再重复解析 result.get()。只需重新查询最新板状态确认协调者是否真的
                                        // 调用过该工具——board 变量是本轮 executeBoard 开始前的旧快照，工具调用
                                        // 发生在 execution 期间，必须重新查询而非信任内存里的旧引用。
                                        var latestBoard =
                                                boards.find(
                                                                parentContext.tenantId(),
                                                                parentContext.taskId())
                                                        .orElseThrow();
                                        var latestCoordinator =
                                                latestBoard.subTasks().get(subTask.subTaskId());
                                        if (latestCoordinator == null
                                                || latestCoordinator.status()
                                                        != TaskBoard.Status.COMPLETED) {
                                            // "只说不做"：模型产出了文本回复但从未调用 submit_coordination_plan
                                            boards.failSubTask(
                                                    parentContext.tenantId(),
                                                    parentContext.taskId(),
                                                    subTask.subTaskId(),
                                                    "协调者未调用 submit_coordination_plan 提交计划",
                                                    true,
                                                    parentContext.lease());
                                        } else {
                                            log.debug(
                                                    "[Assistant协调] 协调计划已冻结：taskId={}，coordinatorExecutionId={}",
                                                    parentContext.taskId().value(),
                                                    subTask.executionId().value());
                                        }
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

    /**
     * ADR-006 三段式：{@code requiresPlan} 的节点先确保有已批准计划，再按计划步骤执行；未批准前零副作用。
     *
     * <p><b>不设独立审批关卡（ADR-006「决策推翻」2026-09-01）</b>：提交计划这个动作不新增执行面、不引入新的
     * Agent 身份，风险已由协调者派发子节点时的既有审批点与步骤执行阶段的工具授权链路覆盖，因此计划提交后固定批准，
     * 不存在转人工的中间态。
     *
     * <p>状态流转（跨多次 {@code executeSubTask} 调用，节点每轮只处于一种）：
     *
     * <ul>
     *   <li>无活跃计划 → {@link #runPlanningExecution} 派发一次 planning execution（{@code READ_ONLY}），
     *       结束后节点按结果转 {@code RETRYABLE}（已提交并批准，等待重新领取）或 {@code RETRYABLE}（未提交，规划失败），
     *       均不停留 {@code RUNNING}
     *   <li>活跃计划为 {@code APPROVED} → {@code claimApproved} 冻结 revision 进入 {@code EXECUTING}，再走
     *       {@link #executeApprovedPlanSteps} 用现有执行链路完成业务动作
     *   <li>活跃计划为 {@code EXECUTING}（重入，如恢复场景）→ 直接走 {@link #executeApprovedPlanSteps}
     * </ul>
     */
    private Flux<ExecutionEvent> executePlannedSubTask(
            AssistantCommand parentCommand,
            InvocationContext parentContext,
            TaskBoard board,
            SubTask subTask) {
        var active =
                plans.findActive(
                        parentContext.tenantId(), parentContext.taskId(), subTask.subTaskId());
        if (active.isEmpty()) {
            return runPlanningExecution(parentCommand, parentContext, subTask);
        }
        var plan = active.get();
        if (plan.status() == ExecutorPlan.Status.APPROVED) {
            plans.claimApproved(
                    new ExecutorPlanPort.ClaimApprovedCommand(
                            parentContext.tenantId(),
                            plan.planId(),
                            plan.lockVersion(),
                            clock.instant()));
            emitPlanEvent(
                    parentContext,
                    ExecutionEventType.EXECUTOR_PLAN_EXECUTION_STARTED,
                    ExecutionEventStatus.RUNNING,
                    planValues(plan.planId(), ExecutorPlan.Status.EXECUTING.name()),
                    "execution-started-" + plan.planId() + "-r" + plan.revision());
        } else if (plan.status() != ExecutorPlan.Status.EXECUTING) {
            log.debug(
                    "[Assistant协调] 计划处于非执行态，本轮暂停：taskId={}，planId={}，status={}",
                    parentContext.taskId().value(),
                    plan.planId(),
                    plan.status());
            return Flux.empty();
        }
        return executeApprovedPlanSteps(parentCommand, parentContext, board, subTask);
    }

    /** 派发一次只读规划 execution；模型只能调只读工具与 {@code submit_executor_plan}，不产生业务副作用。 */
    private Flux<ExecutionEvent> runPlanningExecution(
            AssistantCommand parentCommand, InvocationContext parentContext, SubTask subTask) {
        var draft =
                plans.beginPlanning(
                        new ExecutorPlanPort.BeginPlanningCommand(
                                parentContext.tenantId(),
                                parentContext.taskId(),
                                subTask.subTaskId(),
                                subTask.subTaskId(),
                                subTask.description(),
                                Map.of("roleKey", subTask.roleKey(), "skillKey", subTask.skillKey()),
                                clock.instant()));
        emitPlanEvent(
                parentContext,
                ExecutionEventType.EXECUTOR_PLAN_CREATED,
                ExecutionEventStatus.PLANNING,
                planValues(draft.planId(), ExecutorPlan.Status.PLANNING.name()),
                "created-" + draft.planId());
        var planningInput =
                subTask.description()
                        + "\n\n本轮处于规划阶段（只读）：请先梳理完成本任务需要的有序步骤，"
                        + "只能调用只读工具进行调查，最后调用 submit_executor_plan 提交计划；不要尝试调用任何写工具。";
        var planningCommand =
                parentCommand.forSubTask(
                        subTask,
                        planningInput,
                        parentCommand.lease(),
                        clock.instant(),
                        CoordinationPlan.AggregationContract.Kind.PASS_THROUGH,
                        ExecutionEvent.ControlMode.READ_ONLY);
        log.debug(
                "[Assistant协调] 派发只读 planning execution：taskId={}，subTaskId={}",
                parentContext.taskId().value(),
                subTask.subTaskId());
        return commands.execute(planningCommand)
                .onErrorResume(
                        error -> {
                            log.warn(
                                    "[Assistant协调] planning execution 异常，本轮暂停等待重试：taskId={}，subTaskId={}",
                                    parentContext.taskId().value(),
                                    subTask.subTaskId(),
                                    error);
                            return Flux.empty();
                        })
                .filter(event -> visibleToTaskConsumer(subTask, event))
                .concatWith(
                        Flux.defer(
                                () -> {
                                    // planning execution 已结束：节点当前仍是 RUNNING，必须显式转出，否则
                                    // finalizeBoard 会误判整板无可运行节点而暂停整个委托任务（其余并行节点应能继续跑）。
                                    var latestPlan =
                                            plans.findActive(
                                                    parentContext.tenantId(),
                                                    parentContext.taskId(),
                                                    subTask.subTaskId());
                                    if (latestPlan.isPresent()
                                            && latestPlan.get().status()
                                                    == ExecutorPlan.Status.APPROVED) {
                                        // 提交即批准：转可重新领取，让外层下一轮 executeBoard 递归重新 claim
                                        // 该节点，进入 executePlannedSubTask 后走向 executeApprovedPlanSteps。
                                        boards.interruptSubTask(
                                                parentContext.tenantId(),
                                                parentContext.taskId(),
                                                subTask.subTaskId(),
                                                true,
                                                parentContext.lease());
                                        return Flux.empty();
                                    }
                                    // "只说不做"：规划期结束但未提交——视为本轮规划失败，按既有重试预算处理，
                                    // 不留 RUNNING 悬空（官方文档也记录了模型可能只在文本里描述计划但不真正调用提交工具）。
                                    boards.failSubTask(
                                            parentContext.tenantId(),
                                            parentContext.taskId(),
                                            subTask.subTaskId(),
                                            "规划阶段未产生已提交的计划",
                                            true,
                                            parentContext.lease());
                                    return Flux.empty();
                                }));
    }

    /** 计划已 {@code EXECUTING} 时，按已批准步骤复用现有执行链路推进业务动作。 */
    private Flux<ExecutionEvent> executeApprovedPlanSteps(
            AssistantCommand parentCommand,
            InvocationContext parentContext,
            TaskBoard board,
            SubTask subTask) {
        var childCommand =
                parentCommand.forSubTask(
                        subTask,
                        board.resolveInput(subTask)
                                + "\n\n本轮处于已批准的执行阶段：请按你先前提交并已获批的计划步骤逐条推进，"
                                + "不要重新规划，完成全部步骤后正常结束本次回复。开始执行某个步骤前先调用"
                                + " report_executor_step 上报 STARTED，执行完成后上报 COMPLETED，"
                                + "遇到无法完成的失败上报 FAILED；必须逐条上报，不要跳过或提前上报未满足依赖的步骤。",
                        parentCommand.lease(),
                        clock.instant(),
                        board.goal().aggregationContract().kind());
        var result = new AtomicReference<>("");
        var observedEvents = new java.util.ArrayList<ExecutionEvent>();
        var failure = new AtomicReference<String>();
        var completed = new AtomicBoolean();
        return commands.execute(childCommand)
                .doOnNext(
                        event -> {
                            observedEvents.add(event);
                            if (event.type() == ExecutionEventType.MESSAGE_COMPLETED) {
                                var text = event.payload().values().get("text");
                                if (text != null) result.set(text.toString());
                            }
                            if (event.type() == ExecutionEventType.EXECUTION_COMPLETED) {
                                completed.set(true);
                            }
                            if (event.type() == ExecutionEventType.EXECUTION_FAILED
                                    || event.type() == ExecutionEventType.COMMAND_REJECTED
                                    || event.status() == ExecutionEventStatus.FAILED
                                    || event.status() == ExecutionEventStatus.REJECTED) {
                                failure.compareAndSet(null, "计划执行未通过状态校验");
                            }
                        })
                .onErrorResume(
                        error -> {
                            failure.compareAndSet(null, "计划执行异常");
                            return Flux.empty();
                        })
                .concatWith(
                        Flux.defer(
                                () -> {
                                    var active =
                                            plans.findActive(
                                                    parentContext.tenantId(),
                                                    parentContext.taskId(),
                                                    subTask.subTaskId());
                                    if (failure.get() != null || !completed.get()) {
                                        var failureMessage =
                                                Objects.requireNonNullElse(
                                                        failure.get(), "计划执行未产生完整终态");
                                        active.ifPresent(
                                                plan -> {
                                                    plans.fail(
                                                            parentContext.tenantId(),
                                                            plan.planId(),
                                                            plan.lockVersion(),
                                                            failureMessage,
                                                            clock.instant());
                                                    emitPlanEvent(
                                                            parentContext,
                                                            ExecutionEventType.EXECUTOR_PLAN_FAILED,
                                                            ExecutionEventStatus.FAILED,
                                                            planValues(
                                                                    plan.planId(),
                                                                    ExecutorPlan.Status.FAILED
                                                                            .name()),
                                                            "failed-"
                                                                    + plan.planId()
                                                                    + "-r"
                                                                    + plan.revision());
                                                });
                                        boards.failSubTask(
                                                parentContext.tenantId(),
                                                parentContext.taskId(),
                                                subTask.subTaskId(),
                                                failureMessage,
                                                failure.get() == null
                                                        || isTransientMessage(failure.get()),
                                                parentContext.lease());
                                    } else {
                                        active.ifPresent(
                                                plan -> {
                                                    plans.complete(
                                                            parentContext.tenantId(),
                                                            plan.planId(),
                                                            plan.lockVersion(),
                                                            clock.instant());
                                                    emitPlanEvent(
                                                            parentContext,
                                                            ExecutionEventType
                                                                    .EXECUTOR_PLAN_COMPLETED,
                                                            ExecutionEventStatus.COMPLETED,
                                                            planValues(
                                                                    plan.planId(),
                                                                    ExecutorPlan.Status.COMPLETED
                                                                            .name()),
                                                            "completed-"
                                                                    + plan.planId()
                                                                    + "-r"
                                                                    + plan.revision());
                                                });
                                        boards.completeSubTask(
                                                parentContext.tenantId(),
                                                parentContext.taskId(),
                                                subTask.subTaskId(),
                                                result.get(),
                                                parentContext.lease());
                                        log.debug(
                                                "[Assistant协调] 计划执行已完成：taskId={}，subTaskId={}",
                                                parentContext.taskId().value(),
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

    /**
     * 构造并持久化一个计划级事件（AAF-107 #10705）。{@code ExecutorPlan} 是独立聚合根，不经过 {@code
     * TaskTransition} 机制，需要本类直接持有 {@link ExecutionEventStorePort} 自行 {@code append}——
     * 复用 {@code AssistantApplicationService} 已确立的"事件产生方自己负责持久化后再流出"模式。{@code eventStore}
     * 为 {@code null}（未接入计划能力的部署）时静默跳过，不阻塞主流程。
     */
    private void emitPlanEvent(
            InvocationContext context,
            ExecutionEventType type,
            ExecutionEventStatus status,
            Map<String, Object> payloadValues,
            String eventIdSuffix) {
        if (eventStore == null) {
            return;
        }
        var event =
                new ExecutionEvent(
                        new EventId("executor-plan-" + eventIdSuffix),
                        context.tenantId(),
                        context.conversationId(),
                        context.sessionId(),
                        context.taskId(),
                        context.executionId(),
                        context.runId(),
                        context.parentExecutionId(),
                        0,
                        type,
                        status,
                        context.controlMode(),
                        OwnerType.SYSTEM,
                        context.assistantId(),
                        null,
                        context.userId(),
                        context.correlationId(),
                        context.causationId(),
                        context.idempotencyKey(),
                        new ExecutionEventPayload(payloadValues),
                        clock.instant(),
                        context.nodeIdentity());
        eventStore.append(event, context.lease()).subscribe();
    }

    private static Map<String, Object> planValues(String planId, String status) {
        var values = new LinkedHashMap<String, Object>();
        values.put("planId", planId);
        values.put("status", status);
        return values;
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
                new ToolAuthorizationContext(Map.of()),
                command.nodeIdentity());
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
