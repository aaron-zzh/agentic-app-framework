package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.xuejiai.aaf.framework.intelligent.agent.port.AgentExecutionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task.Owner;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task.OwnerKind;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task.Source;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskCheckpoint;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ExecutionProfileSnapshotPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HitlTransitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskDispatchSignalPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskMaterializationPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskMaterializationPort.ClaimedNode;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskMaterializationPort.MaterializedDispatch;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskMaterializationPort.NodeResultCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskUnitOfWork;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** canonical TaskPlan 命令入口；每次 worker 只领取并执行一个 TaskNode dispatch。 */
@Slf4j
public final class TaskCommandService {
    public static final String TASK_NODE_DISPATCH_TYPE = "task-node-dispatch";

    private final TaskUnitOfWork tasks;
    private final TaskIngress taskIngress;
    private final TaskMaterializationPort materializations;
    private final ExecutionProfileSnapshotPort executionProfiles;
    private final HitlTransitionPort transitions;
    private final ConversationLeasePort conversationLeases;
    private final AssistantCommandPort assistants;
    private final AgentExecutionPort agentExecutions;
    private final TaskDispatchSignalPort dispatchSignals;
    private final Clock clock;
    private final Duration dispatchLeaseTtl;
    private final Duration pauseAckTimeout;

    public TaskCommandService(
            TaskUnitOfWork tasks,
            TaskIngress taskIngress,
            TaskMaterializationPort materializations,
            ExecutionProfileSnapshotPort executionProfiles,
            HitlTransitionPort transitions,
            ConversationLeasePort conversationLeases,
            AssistantCommandPort assistants,
            AgentExecutionPort agentExecutions,
            TaskDispatchSignalPort dispatchSignals,
            Clock clock,
            Duration dispatchLeaseTtl,
            Duration pauseAckTimeout) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
        this.taskIngress = Objects.requireNonNull(taskIngress, "taskIngress 不能为空");
        this.materializations = Objects.requireNonNull(materializations, "materializations 不能为空");
        this.executionProfiles =
                Objects.requireNonNull(executionProfiles, "executionProfiles 不能为空");
        this.transitions = Objects.requireNonNull(transitions, "transitions 不能为空");
        this.conversationLeases =
                Objects.requireNonNull(conversationLeases, "conversationLeases 不能为空");
        this.assistants = Objects.requireNonNull(assistants, "assistants 不能为空");
        this.agentExecutions = Objects.requireNonNull(agentExecutions, "agentExecutions 不能为空");
        this.dispatchSignals = Objects.requireNonNull(dispatchSignals, "dispatchSignals 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
        this.dispatchLeaseTtl = Objects.requireNonNull(dispatchLeaseTtl, "dispatchLeaseTtl 不能为空");
        this.pauseAckTimeout = Objects.requireNonNull(pauseAckTimeout, "pauseAckTimeout 不能为空");
        if (dispatchLeaseTtl.isZero() || dispatchLeaseTtl.isNegative()) {
            throw new IllegalArgumentException("dispatchLeaseTtl 必须为正数");
        }
        if (pauseAckTimeout.isZero() || pauseAckTimeout.isNegative()) {
            throw new IllegalArgumentException("pauseAckTimeout 必须为正数");
        }
    }

    public Task promoteDirect(TenantId tenantId, ExecutionId originExecutionId, String reason) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(originExecutionId, "originExecutionId 不能为空");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("promotion reason 不能为空白");
        }
        var origin =
                tasks.findExecution(tenantId, originExecutionId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("origin DIRECT Execution 不存在"));
        var sourceCommand =
                tasks.findCommand(tenantId, originExecutionId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "origin DIRECT Execution 缺少 command"));
        if (sourceCommand.taskId() != null
                || sourceCommand.operation() != AssistantCommand.Operation.START
                || sourceCommand.executionContract() == null) {
            throw new IllegalStateException("origin command 不是可提升的 DIRECT START");
        }
        var profile =
                executionProfiles
                        .find(tenantId, originExecutionId)
                        .orElseThrow(
                                () -> new IllegalStateException("origin DIRECT Execution 缺少冻结画像"));
        if (!profile.assistantId().equals(sourceCommand.assistantId())
                || profile.invocationPolicy()
                        != com.xuejiai.aaf.framework.intelligent.assistant.model.InvocationPolicy
                                .PRIMARY) {
            throw new IllegalStateException("promotion 必须保持 origin Task Owner Assistant 身份");
        }
        var at = clock.instant();
        var taskId = promotionTaskId(tenantId, originExecutionId);
        var promotedCommand = sourceCommand.promoteToTask(taskId, at);
        var maxAttempts = promotedCommand.executionContract().retryPolicy().maxAttempts();
        var plan =
                TaskPlan.coordinated(
                        taskId,
                        sourceCommand.input(),
                        profile.roleAssignment().roleKey(),
                        null,
                        maxAttempts);
        var owner = new Owner(OwnerKind.ASSISTANT, sourceCommand.assistantId().value());
        var task =
                new Task(
                        origin.tenantId(),
                        origin.userId(),
                        taskId,
                        origin.conversationId(),
                        origin.executionId(),
                        origin.runId(),
                        origin.correlationId(),
                        "execution:" + origin.executionId().value() + ":input",
                        "execution:" + origin.executionId().value() + ":public-context",
                        Source.PROMOTION,
                        0,
                        Task.Status.READY,
                        promotedCommand.controlMode(),
                        owner,
                        promotedCommand.executionContract(),
                        promotedCommand.completionCriteria(),
                        Task.BudgetUsage.empty(),
                        null,
                        null,
                        null,
                        0,
                        null,
                        TaskCheckpoint.empty().withAnnotation("promotionReason", reason.trim()),
                        null,
                        at,
                        at);
        var dispatches = materializations.promoteDirect(origin, task, plan, promotedCommand, at);
        dispatches.forEach(this::signal);
        return tasks.findTask(task.tenantId(), task.taskId()).orElseThrow();
    }

    static TaskId promotionTaskId(TenantId tenantId, ExecutionId originExecutionId) {
        var identity = "promotion|" + tenantId.value() + '|' + originExecutionId.value();
        return new TaskId(
                UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString());
    }

    public Task submit(AssistantCommand command) {
        var maxAttempts =
                Objects.requireNonNull(command.executionContract(), "ExecutionContract 不能为空")
                        .retryPolicy()
                        .maxAttempts();
        return submit(command, TaskPlan.single(command.taskId(), command.input(), maxAttempts));
    }

    public Task submit(AssistantCommand command, TaskPlan plan) {
        return create(command, plan, Source.AUTOMATION).task();
    }

    /** 对话入口只提交 durable Task/Plan/Dispatch；worker 由 signal 唤醒后独立执行。 */
    public Task submitConversation(AssistantCommand command, TaskPlan plan) {
        return create(command, plan, Source.CONVERSATION).task();
    }

    /** 创建 Task/Plan/首批 ready node 后直接消费这些 node-specific dispatch。 */
    public Flux<ExecutionEvent> submitAndDispatch(
            AssistantCommand command, TaskPlan plan, String workerId) {
        var created = create(command, plan, Source.CONVERSATION);
        return Flux.fromIterable(created.dispatches())
                .flatMap(
                        dispatch -> dispatch(dispatch.tenantId(), dispatch.dispatchId(), workerId),
                        plan.maxParallelism());
    }

    /** worker payload 中的稳定标识是 dispatchId，不再用 taskId 猜 current execution。 */
    public Flux<ExecutionEvent> dispatch(TenantId tenantId, String dispatchId, String workerId) {
        return Flux.defer(
                () -> {
                    var claimed =
                            materializations
                                    .claim(
                                            tenantId,
                                            dispatchId,
                                            workerId,
                                            dispatchLeaseTtl,
                                            null,
                                            clock.instant())
                                    .orElse(null);
                    if (claimed == null) {
                        return Flux.empty();
                    }
                    var conversationLease =
                            conversationLeases
                                    .acquire(
                                            tenantId,
                                            claimed.task().conversationId(),
                                            workerId,
                                            dispatchLeaseTtl)
                                    .orElse(null);
                    if (conversationLease == null) {
                        return Flux.empty();
                    }
                    var command =
                            claimed.command()
                                    .withDispatch(
                                            conversationLease,
                                            claimed.dispatch().dispatchId(),
                                            claimed.dispatch().generation(),
                                            claimed.dispatch().fencingToken(),
                                            claimed.dispatch().leaseOwner(),
                                            claimed.dispatch().leaseUntil(),
                                            clock.instant());
                    return executeClaimed(claimed, command)
                            .doFinally(ignored -> conversationLeases.release(conversationLease));
                });
    }

    public Mono<Task> acceptInput(ExecutionInput input) {
        Objects.requireNonNull(input, "input 不能为空");
        return taskIngress
                .accept(input)
                .flatMap(
                        ignored ->
                                Mono.fromCallable(
                                                () -> {
                                                    var task =
                                                            tasks.findTask(
                                                                            input.tenantId(),
                                                                            input.taskId())
                                                                    .orElseThrow(
                                                                            () ->
                                                                                    new IllegalStateException(
                                                                                            "输入关联 Task 不存在"));
                                                    if (input.kind()
                                                            == ExecutionInput.Kind.MODIFY) {
                                                        var scope = input.values().get("scope");
                                                        List<MaterializedDispatch> dispatches;
                                                        if ("TASK_PLAN".equals(scope)) {
                                                            dispatches =
                                                                    materializations
                                                                            .startTaskReplan(
                                                                                    input
                                                                                            .tenantId(),
                                                                                    input.userId(),
                                                                                    input.taskId(),
                                                                                    input.inputId(),
                                                                                    input.text(),
                                                                                    input
                                                                                            .receivedAt());
                                                        } else if ("EXECUTOR_PLAN".equals(scope)) {
                                                            var nodeId =
                                                                    input.values().get("nodeId");
                                                            dispatches =
                                                                    materializations
                                                                            .restartNodeForPlanAmendment(
                                                                                    input
                                                                                            .tenantId(),
                                                                                    input.userId(),
                                                                                    input.taskId(),
                                                                                    nodeId,
                                                                                    input.inputId(),
                                                                                    input.text(),
                                                                                    input
                                                                                            .receivedAt());
                                                        } else {
                                                            throw new IllegalArgumentException(
                                                                    "MODIFY scope 必须是 TASK_PLAN 或 EXECUTOR_PLAN");
                                                        }
                                                        dispatches.forEach(this::signal);
                                                        return tasks.findTask(
                                                                        input.tenantId(),
                                                                        input.taskId())
                                                                .orElseThrow();
                                                    }
                                                    if (input.kind()
                                                            != ExecutionInput.Kind.SUPPLEMENT) {
                                                        return task;
                                                    }
                                                    var lease =
                                                            conversationLeases
                                                                    .acquire(
                                                                            input.tenantId(),
                                                                            task.conversationId(),
                                                                            "clarification-resume-"
                                                                                    + input
                                                                                            .requestId(),
                                                                            dispatchLeaseTtl)
                                                                    .orElseThrow(
                                                                            () ->
                                                                                    new IllegalStateException(
                                                                                            "无法取得 clarification resume conversation lease"));
                                                    try {
                                                        var commit =
                                                                transitions.resumeClarification(
                                                                        input, lease);
                                                        if (commit.resumed()) {
                                                            resumeReadyNodes(
                                                                    input.tenantId(),
                                                                    input.taskId(),
                                                                    commit.executionId(),
                                                                    input.receivedAt());
                                                        }
                                                        return commit.task();
                                                    } finally {
                                                        conversationLeases.release(lease);
                                                    }
                                                })
                                        .subscribeOn(
                                                reactor.core.scheduler.Schedulers
                                                        .boundedElastic()));
    }

    public List<MaterializedDispatch> resumeReadyNodes(
            TenantId tenantId, TaskId taskId, ExecutionId executionId, Instant at) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Objects.requireNonNull(at, "at 不能为空");
        var execution =
                tasks.findExecution(tenantId, executionId)
                        .orElseThrow(() -> new IllegalArgumentException("恢复关联 Execution 不存在"));
        if (!taskId.equals(execution.taskId())) {
            throw new IllegalStateException("恢复关联 Task/Execution 不一致");
        }
        var sourceCommand =
                tasks.findCommand(tenantId, executionId)
                        .orElseThrow(() -> new IllegalStateException("恢复关联 Execution 缺少 command"));
        var dispatches =
                materializations.materializeReadyNodes(tenantId, taskId, sourceCommand, at);
        dispatches.forEach(this::signal);
        return dispatches;
    }

    public Task resolveControllableTask(
            TenantId tenantId,
            UserId userId,
            ConversationId conversationId,
            String requestedTaskId) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(userId, "userId 不能为空");
        Objects.requireNonNull(conversationId, "conversationId 不能为空");
        if (requestedTaskId != null && !requestedTaskId.isBlank()) {
            var task =
                    tasks.findTask(tenantId, new TaskId(requestedTaskId.trim()))
                            .orElseThrow(() -> new IllegalArgumentException("Task 不存在"));
            if (!task.userId().equals(userId)) {
                throw new IllegalArgumentException("Task 不属于当前用户");
            }
            return task;
        }
        var candidates =
                tasks.findTasksByConversation(tenantId, userId, conversationId).stream()
                        .filter(task -> !task.terminal())
                        .toList();
        if (candidates.size() != 1) {
            var ids = candidates.stream().map(task -> task.taskId().value()).toList();
            throw new IllegalStateException("无法唯一确定目标 Task，请明确 taskId。当前候选: " + ids);
        }
        return candidates.getFirst();
    }

    public Task pause(TenantId tenantId, UserId userId, TaskId taskId, String reason) {
        return requestPauseRuntime(
                materializations.requestPause(
                        tenantId, userId, taskId, reason, pauseAckTimeout, clock.instant()));
    }

    public Task takeOver(TenantId tenantId, UserId userId, TaskId taskId, String reason) {
        return requestPauseRuntime(
                materializations.requestTakeOver(
                        tenantId, userId, taskId, reason, pauseAckTimeout, clock.instant()));
    }

    private Task requestPauseRuntime(TaskMaterializationPort.PauseRequest request) {
        if (!request.created() || request.task().status() == Task.Status.PAUSED) {
            return request.task();
        }
        for (var executionId : request.targets()) {
            var accepted =
                    agentExecutions
                            .pause(executionId)
                            .onErrorReturn(false)
                            .blockOptional()
                            .orElse(false);
            if (!accepted) {
                materializations.acknowledgePause(
                        new TaskMaterializationPort.PauseAckCommand(
                                request.task().tenantId(),
                                request.task().taskId(),
                                request.requestId(),
                                executionId,
                                false,
                                null,
                                null,
                                null,
                                "runtime 未接受暂停信号",
                                clock.instant()));
            }
        }
        return tasks.findTask(request.task().tenantId(), request.task().taskId()).orElseThrow();
    }

    public Task resume(TenantId tenantId, UserId userId, TaskId taskId) {
        return dispatchResumed(
                materializations.resumePaused(tenantId, userId, taskId, clock.instant()));
    }

    public Task handBack(TenantId tenantId, UserId userId, TaskId taskId) {
        return dispatchResumed(
                materializations.handBack(tenantId, userId, taskId, clock.instant()));
    }

    private Task dispatchResumed(TaskMaterializationPort.ResumeResult resumed) {
        resumed.nodeDispatches().forEach(this::signal);
        if (resumed.rootDispatchPending()) {
            dispatchRootResume(resumed.task());
        }
        return tasks.findTask(resumed.task().tenantId(), resumed.task().taskId()).orElseThrow();
    }

    private void dispatchRootResume(Task task) {
        var leaseOwner = "task-root:" + UUID.randomUUID();
        var lease =
                conversationLeases
                        .acquire(
                                task.tenantId(),
                                task.conversationId(),
                                leaseOwner,
                                dispatchLeaseTtl)
                        .orElse(null);
        if (lease == null) {
            log.debug(
                    "TASK_ROOT 暂未取得 conversation lease，保留 PENDING 供 scheduler 重试：taskId={}",
                    task.taskId().value());
            return;
        }
        var claimed =
                tasks.claim(task.tenantId(), task.taskId(), lease, clock.instant()).orElse(null);
        if (claimed == null) {
            conversationLeases.release(lease);
            return;
        }
        assistants
                .execute(claimed.command())
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .doFinally(ignored -> conversationLeases.release(lease))
                .subscribe(
                        ignored -> {},
                        failure ->
                                log.warn(
                                        "TASK_ROOT 异步执行失败：taskId={}，executionId={}，错误={}",
                                        task.taskId().value(),
                                        claimed.execution().executionId().value(),
                                        failure.getMessage()));
    }

    public Task cancel(TenantId tenantId, UserId userId, TaskId taskId, String reason) {
        return materializations.requestCancellation(
                tenantId, userId, taskId, reason, clock.instant());
    }

    public int recoverAndDispatch(String workerId, int limit) {
        var at = clock.instant();
        var finalized = 0;
        for (var candidate : materializations.findCanceling(limit)) {
            if (materializations.finalizeCancellation(
                    candidate.tenantId(), candidate.taskId(), at)) {
                finalized++;
            }
        }
        for (var candidate : materializations.findPausing(limit)) {
            if (!at.isBefore(candidate.deadlineAt())
                    && materializations.finalizeTimedOutPause(
                            candidate.tenantId(), candidate.taskId(), at)) {
                finalized++;
            }
        }
        var recovered = materializations.recoverExpired(at) + tasks.recoverExpired(at);
        materializations.findDue(at, limit).forEach(this::signal);
        tasks.findDispatchable(at, limit)
                .forEach(candidate -> dispatchRootResume(candidate.snapshot().task()));
        return finalized + recovered;
    }

    private Flux<ExecutionEvent> executeClaimed(ClaimedNode claimed, AssistantCommand command) {
        var result = new AtomicReference<>("");
        var failure = new AtomicReference<String>();
        var completed = new AtomicBoolean();
        return assistants
                .execute(command)
                .doOnNext(
                        event -> {
                            if (event.type() == ExecutionEventType.MESSAGE_COMPLETED) {
                                var text = event.payload().values().get("text");
                                if (text != null) {
                                    result.set(text.toString());
                                }
                            }
                            if (event.type() == ExecutionEventType.EXECUTION_COMPLETED) {
                                completed.set(true);
                            }
                            if (event.type() == ExecutionEventType.EXECUTION_FAILED
                                    || event.type() == ExecutionEventType.COMMAND_REJECTED) {
                                failure.compareAndSet(null, "TaskNode execution failed");
                            }
                        })
                .onErrorResume(
                        throwable -> {
                            failure.compareAndSet(
                                    null,
                                    Objects.requireNonNullElse(
                                            throwable.getMessage(), "TaskNode execution failed"));
                            return Flux.empty();
                        })
                .concatWith(
                        Flux.defer(
                                () -> {
                                    var terminalFailure = failure.get();
                                    if (!completed.get() && terminalFailure == null) {
                                        terminalFailure =
                                                "TaskNode execution did not reach terminal state";
                                    }
                                    var commit =
                                            materializations.commit(
                                                    new NodeResultCommand(
                                                            command.tenantId(),
                                                            command.taskId(),
                                                            claimed.plan().planId(),
                                                            claimed.plan().revision(),
                                                            claimed.node().nodeId(),
                                                            command.executionId().value(),
                                                            claimed.execution().attemptNo(),
                                                            command.dispatchId(),
                                                            command.dispatchGeneration(),
                                                            command.dispatchFencingToken(),
                                                            command.dispatchLeaseOwner(),
                                                            claimed.taskVersion(),
                                                            claimed.planVersion(),
                                                            claimed.nodeVersion(),
                                                            claimed.executionVersion(),
                                                            result.get(),
                                                            terminalFailure,
                                                            clock.instant()));
                                    commit.releasedDispatches().forEach(this::signal);
                                    return Flux.empty();
                                }));
    }

    private CreatedTask create(AssistantCommand command, TaskPlan plan, Source source) {
        Objects.requireNonNull(command, "command 不能为空");
        Objects.requireNonNull(plan, "plan 不能为空");
        if (command.taskId() == null || !command.taskId().equals(plan.taskId())) {
            throw new IllegalArgumentException("TaskPlan 与命令 taskId 必须一致");
        }
        if (command.executionContract() == null
                || command.operation() != AssistantCommand.Operation.START) {
            throw new IllegalArgumentException("planned Task 必须由带完整合同的 START 命令创建");
        }
        var at = command.requestedAt();
        var owner = new Owner(OwnerKind.ASSISTANT, command.assistantId().value());
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
                        0,
                        Task.Status.READY,
                        command.controlMode(),
                        owner,
                        command.executionContract(),
                        command.completionCriteria(),
                        Task.BudgetUsage.empty(),
                        null,
                        null,
                        null,
                        0,
                        null,
                        TaskCheckpoint.empty(),
                        null,
                        at,
                        at);
        var dispatches = materializations.createPlannedTask(task, plan, command, at);
        dispatches.forEach(this::signal);
        return new CreatedTask(
                tasks.findTask(command.tenantId(), command.taskId()).orElseThrow(), dispatches);
    }

    private void signal(MaterializedDispatch dispatch) {
        dispatchSignals.signal(dispatch.tenantId(), dispatch.taskId(), dispatch.dispatchId());
    }

    private record CreatedTask(Task task, List<MaterializedDispatch> dispatches) {
        private CreatedTask {
            dispatches = List.copyOf(dispatches);
        }
    }
}
