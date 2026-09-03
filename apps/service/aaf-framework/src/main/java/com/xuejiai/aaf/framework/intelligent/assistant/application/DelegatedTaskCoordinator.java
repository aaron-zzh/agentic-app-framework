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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.xuejiai.aaf.framework.engine.task.agent.AgentTaskRuntime;
import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentExecutionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ClarificationRequest;
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
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.ClarificationRequestTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.InputTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.IterationEvaluationTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.ParentFailureTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.plan.ExecutorPlan;
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
import com.xuejiai.aaf.framework.intelligent.assistant.port.plan.ExecutorPlanPort;
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
                null,
                null);
    }

    /**
     * 完整构造器：新增 {@code plans}（协调者/执行者节点在自己 execution 内自主决定要不要先规划的局部计划状态机端口， AAF-107 选项 B
     * 架构改造，2026-09-02）与 {@code events}（计划级事件持久化——{@code ExecutorPlan} 是独立聚合根， 不经过 {@code
     * TaskTransition} 机制，需要本类直接持有 {@link ExecutionEventStorePort} 自行 {@code append}）。两者均允许为 {@code
     * null}——尚未接入计划能力的部署（如测试固件）用不到它们，此时 {@code executeSubTask} 每轮查询 {@code plans.findActive(...)}
     * 前会先判空短路，等价于该能力从未启用。
     *
     * <p>是否规划不再由建板时的静态判定决定（原 {@code PlanRequirementPolicy}/{@code requiresPlan} 静态标志已随本次
     * 改造删除）——任何节点（协调者或执行者）都在自己的 execution 内自主选择要不要调用 {@code submit_executor_plan}，{@code
     * executeSubTask} 只负责运行时查询"这个节点现在有没有活跃计划"来决定分流。
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
    //
    // AAF-107 选项 B 架构改造（2026-09-02）：不再有独立的前置 L0 分类器判断 single/coordinated，也不再有
    // "先只读规划、再切一次执行 execution"的两阶段流程——协调者或执行者在同一次 execution 内自主判断三档
    // （直答/拆步骤/拆多智能体）并直接采取行动。任务复杂度判断指导文案走内置 Skill（builtin-task-decomposition，
    // AAF-107 dev-log 已记录），不在这里硬编码——与 builtin-self-learning 等既有内置行为指导保持同一套机制，
    // 运营方可独立迭代措辞而不需要改代码重新部署。
    Flux<ExecutionEvent> executeSubTask(
            AssistantCommand parentCommand,
            InvocationContext parentContext,
            TaskBoard board,
            SubTask subTask) {
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
                                () ->
                                        finalizeSubTaskExecution(
                                                parentCommand,
                                                parentContext,
                                                board,
                                                subTask,
                                                awaitingAuthorization.get(),
                                                clarification.get(),
                                                failure.get(),
                                                completed.get(),
                                                result.get(),
                                                observedEvents)))
                .filter(event -> visibleToTaskConsumer(subTask, event));
    }

    /**
     * 单次 execution 结束后的统一收尾判断（AAF-107 选项 B 架构改造，2026-09-02，替代原两阶段 {@code
     * executePlannedSubTask}/{@code runPlanningExecution}/{@code executeApprovedPlanSteps}）。
     *
     * <p>借鉴官方 Harness Plan Mode 的判断方式（核实 {@code plan-mode.html} 后确认"只看最终状态判断成功与否有歧义，
     * 必须结合是否真的调用过计划/协调工具"）：不能只用一个布尔值判断，按以下优先级依次判定四态：
     *
     * <ul>
     *   <li><b>协调派生</b>：{@code kind==COORDINATOR} 且节点自身状态已被 {@code
     *       SubmitCoordinationPlanTool.submit → boards.applyCoordinationPlan} 同步转 {@code COMPLETED}
     *       ——查询最新板状态确认，{@code board} 是本轮 {@code executeBoard} 开始前的旧快照，工具调用发生在 execution
     *       期间，必须重新查询而非信任内存里的旧引用
     *   <li><b>步骤计划</b>：查询 {@code plans.findActive(...)} 发现本节点有活跃 {@code ExecutorPlan} ——{@code
     *       submit_executor_plan} 提交即直接进入 {@code EXECUTING}（不再有独立 {@code APPROVED}
     *       等待认领中间态，因为提交和执行在同一次 execution 里连续发生，没有"等待另一次调用认领"的时间差）， execution 结束时按完成证据判断计划成功/失败
     *   <li><b>简单直答</b>：从未调用任何计划/协调工具，直接产出文本——按原有 {@code completionEvidenceSatisfied} 校验
     *   <li><b>"只说不做"</b>：以上均不满足（如声称要拆步骤但从未真正提交计划）——按失败处理，不静默放行
     * </ul>
     */
    private Flux<ExecutionEvent> finalizeSubTaskExecution(
            AssistantCommand parentCommand,
            InvocationContext parentContext,
            TaskBoard board,
            SubTask subTask,
            boolean awaitingAuthorization,
            ClarificationRequest clarification,
            String failureMessage,
            boolean completed,
            String result,
            List<ExecutionEvent> observedEvents) {
        if (awaitingAuthorization) {
            return Flux.empty();
        }
        if (clarification != null) {
            var event = clarificationRequestedEvent(parentContext, clarification, clock.instant());
            transitions.requestClarification(
                    new ClarificationRequestTransition(parentContext, clarification, event));
            return Flux.just(event);
        }
        if (failureMessage != null || !completed) {
            failOrRecordPlanFailure(
                    parentContext,
                    subTask,
                    Objects.requireNonNullElse(failureMessage, "子任务未产生完整终态"),
                    failureMessage == null || isTransientMessage(failureMessage));
            return Flux.empty();
        }
        if (subTask.kind() == SubTask.Kind.COORDINATOR
                && coordinatorSubmittedPlan(parentContext, subTask)) {
            log.debug(
                    "[Assistant协调] 协调计划已冻结：taskId={}，coordinatorExecutionId={}",
                    parentContext.taskId().value(),
                    subTask.executionId().value());
            return Flux.empty();
        }
        if (subTask.kind() == SubTask.Kind.EVALUATOR) {
            var evaluation = decodeIterationEvaluation(result);
            var at = clock.instant();
            var event = iterationEvent(parentContext, board.goal().iteration(), evaluation, at);
            var committed =
                    transitions.evaluateIteration(
                            new IterationEvaluationTransition(
                                    parentContext, subTask.subTaskId(), evaluation, event, at));
            return Flux.just(committed.event());
        }
        var activePlan =
                plans == null
                        ? Optional.<ExecutorPlan>empty()
                        : plans.findActive(
                                parentContext.tenantId(),
                                parentContext.taskId(),
                                subTask.subTaskId());
        if (activePlan.isPresent()) {
            return finalizePlannedStepExecution(parentContext, subTask, activePlan.get(), result);
        }
        if ((subTask.kind() == SubTask.Kind.EXECUTOR || subTask.kind() == SubTask.Kind.AGGREGATOR)
                && !completionEvidenceSatisfied(parentCommand, observedEvents)) {
            // "只说不做"：产出了文本但完成证据不满足，也没有提交任何计划——不能静默放行
            failOrRecordPlanFailure(parentContext, subTask, "子任务未产生完整终态", true);
            return Flux.empty();
        }
        boards.completeSubTask(
                parentContext.tenantId(),
                parentContext.taskId(),
                subTask.subTaskId(),
                result,
                parentContext.lease());
        log.debug(
                "[Assistant协调] 执行子 Agent 已完成：taskId={}，executionId={}，agentKey={}",
                parentContext.taskId().value(),
                subTask.executionId().value(),
                subTask.subTaskId());
        return Flux.empty();
    }

    /** 重新查询最新板状态确认协调者是否真的调用过 {@code submit_coordination_plan}（"只说不做"防护）。 */
    private boolean coordinatorSubmittedPlan(InvocationContext parentContext, SubTask subTask) {
        var latestBoard =
                boards.find(parentContext.tenantId(), parentContext.taskId()).orElseThrow();
        var latestCoordinator = latestBoard.subTasks().get(subTask.subTaskId());
        return latestCoordinator != null
                && latestCoordinator.status() == TaskBoard.Status.COMPLETED;
    }

    /** 步骤计划的完成/失败判定：完成证据仍是唯一真理源，计划本身只是审计记录，不替代原有校验。 */
    private Flux<ExecutionEvent> finalizePlannedStepExecution(
            InvocationContext parentContext, SubTask subTask, ExecutorPlan plan, String result) {
        if (plan.status() == ExecutorPlan.Status.EXECUTING) {
            plans.complete(
                    parentContext.tenantId(), plan.planId(), plan.lockVersion(), clock.instant());
            emitPlanEvent(
                    parentContext,
                    ExecutionEventType.EXECUTOR_PLAN_COMPLETED,
                    ExecutionEventStatus.COMPLETED,
                    planValues(plan.planId(), ExecutorPlan.Status.COMPLETED.name()),
                    "completed-" + plan.planId() + "-r" + plan.revision());
        }
        boards.completeSubTask(
                parentContext.tenantId(),
                parentContext.taskId(),
                subTask.subTaskId(),
                result,
                parentContext.lease());
        log.debug(
                "[Assistant协调] 计划执行已完成：taskId={}，subTaskId={}",
                parentContext.taskId().value(),
                subTask.subTaskId());
        return Flux.empty();
    }

    /** 子任务失败统一入口：若存在活跃计划一并标记失败，保持计划与板状态一致。 */
    private void failOrRecordPlanFailure(
            InvocationContext parentContext,
            SubTask subTask,
            String failureMessage,
            boolean retryable) {
        if (plans != null) {
            plans.findActive(parentContext.tenantId(), parentContext.taskId(), subTask.subTaskId())
                    .filter(plan -> plan.status() == ExecutorPlan.Status.EXECUTING)
                    .ifPresent(
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
                                                plan.planId(), ExecutorPlan.Status.FAILED.name()),
                                        "failed-" + plan.planId() + "-r" + plan.revision());
                            });
        }
        boards.failSubTask(
                parentContext.tenantId(),
                parentContext.taskId(),
                subTask.subTaskId(),
                failureMessage,
                retryable,
                parentContext.lease());
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
                at,
                context.nodeIdentity());
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
                at,
                context.nodeIdentity());
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
                at,
                context.nodeIdentity());
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
     * 构造并持久化一个计划级事件（AAF-107 #10705）。{@code ExecutorPlan} 是独立聚合根，不经过 {@code TaskTransition}
     * 机制，需要本类直接持有 {@link ExecutionEventStorePort} 自行 {@code append}—— 复用 {@code
     * AssistantApplicationService} 已确立的"事件产生方自己负责持久化后再流出"模式。{@code eventStore} 为 {@code
     * null}（未接入计划能力的部署）时静默跳过，不阻塞主流程。
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
