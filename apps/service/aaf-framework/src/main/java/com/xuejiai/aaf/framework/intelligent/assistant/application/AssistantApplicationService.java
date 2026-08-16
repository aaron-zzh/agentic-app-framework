package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand;
import com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage;
import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ModelSelectionRequirement;
import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext.ToolAuthorizationRule;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentExecutionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask.OwnerKind;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask.RecoveryPoint;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask.TaskActor;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask.TaskOwner;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask.TaskStatus;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionDecision;
import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillRoute;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.EffectiveContextPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskBoardPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskControlPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskRecoveryPort;
import com.xuejiai.aaf.framework.intelligent.cognition.application.MemoryGovernanceService;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryContextPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryRecallPort.RecallQuery;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AgentId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;

import reactor.core.publisher.Flux;

/** P2 Assistant 唯一应用用例，不感知 Spring、JPA 或 AgentScope。 */
public final class AssistantApplicationService implements AssistantCommandPort {

    private final AssistantDefinitionPort definitions;
    private final TaskControlPort tasks;
    private final TaskBoardPort taskBoards;
    private final SkillRouter skillRouter;
    private final EffectiveSkillResolver effectiveSkillResolver;
    private final EffectiveContextPort effectiveContexts;
    private final MemoryContextPort memoryContexts;
    private final MemoryGovernanceService memoryGovernance;
    private final AgentExecutionPort agentExecution;
    private final CapabilityRouter models;
    private final CompletionValidator completionValidator;
    private final ExecutionEventStorePort eventStore;
    private final TaskRecoveryPort recoveries;

    public AssistantApplicationService(
            AssistantDefinitionPort definitions,
            TaskControlPort tasks,
            TaskBoardPort taskBoards,
            SkillRouter skillRouter,
            EffectiveSkillResolver effectiveSkillResolver,
            EffectiveContextPort effectiveContexts,
            MemoryContextPort memoryContexts,
            MemoryGovernanceService memoryGovernance,
            AgentExecutionPort agentExecution,
            CapabilityRouter models,
            CompletionValidator completionValidator,
            ExecutionEventStorePort eventStore,
            TaskRecoveryPort recoveries) {
        this.definitions = Objects.requireNonNull(definitions, "definitions 不能为空");
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
        this.taskBoards = Objects.requireNonNull(taskBoards, "taskBoards 不能为空");
        this.skillRouter = Objects.requireNonNull(skillRouter, "skillRouter 不能为空");
        this.effectiveSkillResolver =
                Objects.requireNonNull(effectiveSkillResolver, "effectiveSkillResolver 不能为空");
        this.effectiveContexts =
                Objects.requireNonNull(effectiveContexts, "effectiveContexts 不能为空");
        this.memoryContexts = Objects.requireNonNull(memoryContexts, "memoryContexts 不能为空");
        this.memoryGovernance = Objects.requireNonNull(memoryGovernance, "memoryGovernance 不能为空");
        this.agentExecution = Objects.requireNonNull(agentExecution, "agentExecution 不能为空");
        this.models = Objects.requireNonNull(models, "models 不能为空");
        this.completionValidator =
                Objects.requireNonNull(completionValidator, "completionValidator 不能为空");
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore 不能为空");
        this.recoveries = Objects.requireNonNull(recoveries, "recoveries 不能为空");
    }

    @Override
    public Flux<ExecutionEvent> invoke(AssistantInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        var command = invocation.command();
        if (command.controlMode()
                        == com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent
                                .ControlMode.DELEGATED
                && command.lease() == null) {
            throw new IllegalStateException("DELEGATED 执行必须由持久调度器注入 conversation lease");
        }
        if (command.operation().executesAgent()
                && command.controlMode()
                        != com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent
                                .ControlMode.DELEGATED) {
            recoveries.saveCommand(command);
        }
        var failureSequence = new AtomicLong(command.sequenceBase());
        var taskRef = new AtomicReference<AssistantTask>();
        var taskProgressed = new AtomicBoolean();
        return Flux.defer(
                        () ->
                                command.operation() == AssistantCommand.Operation.SUBTASK
                                        ? executeSubTask(invocation)
                                        : command.operation().executesAgent()
                                                ? executeAgent(invocation, taskRef, taskProgressed)
                                                : controlTask(command))
                .doOnNext(event -> failureSequence.accumulateAndGet(event.sequence(), Math::max))
                .onErrorResume(
                        failure ->
                                failTask(
                                        command,
                                        taskRef,
                                        taskProgressed.get(),
                                        failureSequence.incrementAndGet(),
                                        failure))
                .concatMap(
                        event ->
                                event.ownerType() == OwnerType.ASSISTANT
                                        ? eventStore.append(event, command.lease())
                                        : reactor.core.publisher.Mono.just(event));
    }

    private Flux<ExecutionEvent> executeSubTask(AssistantInvocation invocation) {
        var command = invocation.command();
        var definition = requireDefinition(command);
        definition.requireControlMode(command.controlMode());
        requirePublished(definition);
        var route = resolveRoute(invocation, definition, "子任务没有可用的 SkillRoute");
        if (!definition.toolPolicy().allows(command.controlMode(), route.actionEffect())) {
            throw new IllegalStateException("当前控制模式禁止子任务动作: " + route.actionKey());
        }
        var memoryContext =
                longTermMemoryEnabled(invocation, definition)
                        ? memoryContexts.prepare(
                                new RecallQuery(
                                        command.memorySubject(),
                                        command.input(),
                                        4,
                                        1024,
                                        command.requestedAt()))
                        : MemoryContextPort.MemoryContext.empty();
        return agentExecution.execute(
                agentCommand(
                        invocation,
                        route,
                        definition,
                        command.sequenceBase(),
                        memoryContext.messages()));
    }

    private Flux<ExecutionEvent> executeAgent(
            AssistantInvocation invocation,
            AtomicReference<AssistantTask> taskRef,
            AtomicBoolean taskProgressed) {
        var command = invocation.command();
        var sequence = new AtomicLong(command.sequenceBase());
        var emitted = new ArrayList<ExecutionEvent>();
        var task =
                command.operation() == AssistantCommand.Operation.START
                        ? prepareTask(command, sequence, emitted, taskRef, taskProgressed)
                        : requireTask(command);
        taskRef.set(task);
        if (command.operation() == AssistantCommand.Operation.RESUME) {
            requireResumable(task);
        }

        var definition = requireDefinition(command);
        definition.requireControlMode(command.controlMode());
        requirePublished(definition);
        if (command.operation() == AssistantCommand.Operation.RESUME) {
            task = prepareTask(command, sequence, emitted, taskRef, taskProgressed);
            taskRef.set(task);
        }
        var route = resolveRoute(invocation, definition, "没有可用的 SkillRoute");
        if (!definition.toolPolicy().allows(command.controlMode(), route.actionEffect())) {
            throw new IllegalStateException("当前控制模式禁止业务动作: " + route.actionKey());
        }

        var memoryContext =
                longTermMemoryEnabled(invocation, definition)
                        ? memoryContexts.prepare(
                                new RecallQuery(
                                        command.memorySubject(),
                                        command.input(),
                                        8,
                                        2048,
                                        command.requestedAt()))
                        : MemoryContextPort.MemoryContext.empty();
        var contextCandidates = new ArrayList<>(command.contextCandidates());
        memoryContext
                .references()
                .forEach(
                        reference ->
                                contextCandidates.add(
                                        new EffectiveContextManifest.SourceReference(
                                                EffectiveContextManifest.SourceType.MEMORY,
                                                reference.memoryId(),
                                                "1",
                                                reference.scope(),
                                                "与当前任务语义相关且在记忆预算内",
                                                reference.redactedSummary(),
                                                true)));
        var manifest =
                effectiveContexts.resolve(
                        command.tenantId(),
                        command.userId(),
                        definition,
                        task,
                        route,
                        contextCandidates,
                        command.requestedAt());
        task = moveToRunning(command, task, sequence, emitted, route, manifest);
        taskRef.set(task);
        taskProgressed.set(true);
        var agentEvents = new ArrayList<ExecutionEvent>();
        var agentCommand =
                agentCommand(
                        invocation, route, definition, sequence.get(), memoryContext.messages());

        var executionEvents =
                agentExecution
                        .execute(agentCommand)
                        .doOnNext(
                                event -> {
                                    agentEvents.add(event);
                                    sequence.accumulateAndGet(event.sequence(), Math::max);
                                });
        return Flux.concat(
                Flux.fromIterable(emitted),
                executionEvents,
                Flux.defer(() -> finalizeTask(invocation, taskRef, route, agentEvents, sequence)));
    }

    private Flux<ExecutionEvent> controlTask(AssistantCommand command) {
        var task = requireTask(command);
        var sequence = new AtomicLong(command.sequenceBase());
        if (task.status() == TaskStatus.COMPLETED || task.status() == TaskStatus.CANCELED) {
            throw new IllegalStateException("终态任务不能执行控制命令: " + task.status());
        }
        var next =
                switch (command.operation()) {
                    case CANCEL -> TaskStatus.CANCELED;
                    case PAUSE, TAKE_OVER -> TaskStatus.PAUSED;
                    case START, RESUME, SUBTASK -> throw new IllegalStateException("非法控制命令");
                };
        var owner =
                command.operation() == AssistantCommand.Operation.TAKE_OVER
                        ? humanOwner(command)
                        : task.owner();
        var reason =
                switch (command.operation()) {
                    case CANCEL -> "用户取消任务";
                    case PAUSE -> "用户暂停任务";
                    case TAKE_OVER -> "用户接管任务";
                    case START, RESUME, SUBTASK -> throw new IllegalStateException("非法控制命令");
                };
        var recovery =
                next == TaskStatus.PAUSED ? new RecoveryPoint("user-control", "从用户控制点恢复") : null;
        var changed =
                task.transitionTo(
                        next, reason, humanActor(command), owner, recovery, command.requestedAt());
        return agentExecution
                .cancel(command.executionId())
                .flatMapMany(
                        ignored -> {
                            var saved = tasks.save(command.tenantId(), changed, command.lease());
                            var type =
                                    next == TaskStatus.CANCELED
                                            ? ExecutionEventType.EXECUTION_CANCELED
                                            : ExecutionEventType.EXECUTION_PAUSED;
                            return Flux.just(
                                    taskEvent(
                                            command,
                                            sequence.incrementAndGet(),
                                            type,
                                            saved,
                                            reason,
                                            null,
                                            null));
                        });
    }

    /**
     * 长期记忆是否对本次执行生效：需要 assistant definition 配置开启，且主体为 {@link SubjectKind#USER}。
     *
     * <p>访客（{@code SubjectKind.VISITOR}，如未登录渠道用户）降级为短期会话上下文（产品决策，2026-08-01）：
     * 不触发结构化抽取/embedding/向量检索去重，也不参与登录后记忆合并。渠道内的多轮对话仍靠 {@code ShortTermMemoryService}
     * 维持上下文，仅访客登录转正后才会开始积累长期记忆。
     */
    static boolean longTermMemoryEnabled(
            AssistantInvocation invocation, AssistantDefinition definition) {
        var command = invocation.command();
        return invocation.memoryMode() != AssistantInvocation.MemoryMode.DISABLED
                && definition.memoryStrategy().longTermEnabled()
                && command.memorySubject().kind() == SubjectKind.USER;
    }

    static boolean longTermMemoryEnabled(AssistantCommand command, AssistantDefinition definition) {
        return longTermMemoryEnabled(AssistantInvocation.of(command), definition);
    }

    private SkillRoute resolveRoute(
            AssistantInvocation invocation,
            AssistantDefinition definition,
            String missingRouteMessage) {
        if (invocation.requestedSkillKey() == null) {
            var command = invocation.command();
            return skillRouter
                    .route(definition, command.input(), command.userId())
                    .orElseThrow(() -> new IllegalStateException(missingRouteMessage));
        }
        return definition.skillRoutes().stream()
                .filter(route -> route.skillKey().equals(invocation.requestedSkillKey()))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Assistant 未配置请求的 SkillRoute: "
                                                + invocation.requestedSkillKey()));
    }

    private AssistantDefinition requireDefinition(AssistantCommand command) {
        return definitions
                .findByIdAndVersion(
                        command.tenantId(), command.assistantId(), command.assistantVersion())
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Assistant 定义不存在: "
                                                + command.assistantId().value()
                                                + "@"
                                                + command.assistantVersion()));
    }

    private static void requirePublished(AssistantDefinition definition) {
        if (definition.lifecycle() != AssistantDefinition.Lifecycle.PUBLISHED) {
            throw new IllegalStateException("Assistant 定义不可执行: " + definition.lifecycle());
        }
    }

    private AssistantTask prepareTask(
            AssistantCommand command,
            AtomicLong sequence,
            List<ExecutionEvent> emitted,
            AtomicReference<AssistantTask> taskRef,
            AtomicBoolean taskProgressed) {
        if (command.operation() == AssistantCommand.Operation.START) {
            var draft =
                    AssistantTask.draft(
                            command.taskId(),
                            command.controlMode(),
                            assistantOwner(command),
                            "用户创建任务",
                            humanActor(command),
                            command.requestedAt());
            var created = tasks.create(command.tenantId(), draft, command.lease());
            taskRef.set(created);
            taskProgressed.set(true);
            emitted.add(
                    taskEvent(
                            command,
                            sequence.incrementAndGet(),
                            ExecutionEventType.EXECUTION_STARTED,
                            created,
                            "用户创建任务",
                            null,
                            null));
            var planning =
                    created.transitionTo(
                            TaskStatus.PLANNING,
                            "Assistant 开始规划",
                            assistantActor(command),
                            assistantOwner(command),
                            new RecoveryPoint("planning", "从技能路由前恢复"),
                            command.requestedAt());
            tasks.save(command.tenantId(), planning, command.lease());
            taskRef.set(planning);
            emitted.add(
                    taskEvent(
                            command,
                            sequence.incrementAndGet(),
                            ExecutionEventType.TASK_STATUS_CHANGED,
                            planning,
                            "Assistant 开始规划",
                            null,
                            null));
            return planning;
        }

        var task = requireTask(command);
        requireResumable(task);
        if (task.controlMode() != command.controlMode()) {
            task =
                    task.changeControlMode(
                            command.controlMode(),
                            "用户显式切换控制模式",
                            humanActor(command),
                            command.requestedAt());
            tasks.save(command.tenantId(), task, command.lease());
            emitted.add(
                    taskEvent(
                            command,
                            sequence.incrementAndGet(),
                            ExecutionEventType.CONTROL_MODE_CHANGED,
                            task,
                            "用户显式切换控制模式",
                            null,
                            null));
        }
        if (task.status() == TaskStatus.PAUSED || task.status() == TaskStatus.FAILED) {
            task =
                    task.transitionTo(
                            TaskStatus.RECOVERING,
                            "从持久恢复点恢复任务",
                            assistantActor(command),
                            assistantOwner(command),
                            task.recoveryPoint(),
                            command.requestedAt());
            tasks.save(command.tenantId(), task, command.lease());
            emitted.add(
                    taskEvent(
                            command,
                            sequence.incrementAndGet(),
                            ExecutionEventType.RECOVERY_STARTED,
                            task,
                            "从持久恢复点恢复任务",
                            null,
                            null));
        } else if (task.status() == TaskStatus.DRAFT) {
            task =
                    task.transitionTo(
                            TaskStatus.PLANNING,
                            "恢复草稿并开始规划",
                            assistantActor(command),
                            assistantOwner(command),
                            new RecoveryPoint("planning", "从技能路由前恢复"),
                            command.requestedAt());
            tasks.save(command.tenantId(), task, command.lease());
        }
        if (!task.equals(taskRef.get())) {
            taskRef.set(task);
            taskProgressed.set(true);
        }
        return task;
    }

    private AssistantTask moveToRunning(
            AssistantCommand command,
            AssistantTask task,
            AtomicLong sequence,
            List<ExecutionEvent> emitted,
            SkillRoute route,
            EffectiveContextManifest manifest) {
        var running =
                task.transitionTo(
                        TaskStatus.RUNNING,
                        "技能路由完成，交给 Agent 执行",
                        assistantActor(command),
                        new TaskOwner(OwnerKind.AGENT, subagentIdentifier(route)),
                        new RecoveryPoint("agent-execution", "从 Agent 状态槽位恢复"),
                        command.requestedAt());
        tasks.save(command.tenantId(), running, command.lease());
        emitted.add(
                taskEvent(
                        command,
                        sequence.incrementAndGet(),
                        ExecutionEventType.TASK_STATUS_CHANGED,
                        running,
                        "技能路由完成，交给 Agent 执行",
                        eventAgentId(route),
                        manifest));
        return running;
    }

    private Flux<ExecutionEvent> finalizeTask(
            AssistantInvocation invocation,
            AtomicReference<AssistantTask> taskRef,
            SkillRoute route,
            List<ExecutionEvent> agentEvents,
            AtomicLong sequence) {
        var command = invocation.command();
        var canceled =
                agentEvents.stream()
                        .anyMatch(event -> event.status() == ExecutionEventStatus.CANCELED);
        if (canceled) {
            var latest = tasks.find(command.tenantId(), command.taskId()).orElse(taskRef.get());
            if (latest.status() == TaskStatus.CANCELED || latest.status() == TaskStatus.PAUSED) {
                return Flux.empty();
            }
            var canceledTask =
                    latest.transitionTo(
                            TaskStatus.CANCELED,
                            "Agent 执行已取消",
                            humanActor(command),
                            humanOwner(command),
                            null,
                            Instant.now());
            tasks.save(command.tenantId(), canceledTask, command.lease());
            taskRef.set(canceledTask);
            return Flux.just(
                    taskEvent(
                            command,
                            sequence.incrementAndGet(),
                            ExecutionEventType.EXECUTION_CANCELED,
                            canceledTask,
                            "Agent 执行已取消",
                            eventAgentId(route),
                            null));
        }
        var decision =
                completionValidator.validate(
                        new CompletionValidator.ValidationRequest(
                                taskRef.get(),
                                route,
                                command.completionCriteria(),
                                taskBoards.find(command.tenantId(), command.taskId()),
                                agentEvents));
        var events = new ArrayList<ExecutionEvent>();
        var task = taskRef.get();
        events.add(
                taskEvent(
                        command,
                        sequence.incrementAndGet(),
                        ExecutionEventType.VALIDATION_STARTED,
                        task,
                        "开始验证显式业务完成条件",
                        eventAgentId(route),
                        null));

        switch (decision.outcome()) {
            case COMPLETED -> {
                task = verifying(command, task, decision, sequence, events, eventAgentId(route));
                task =
                        transition(
                                command,
                                task,
                                TaskStatus.COMPLETED,
                                decision.reason(),
                                assistantOwner(command),
                                null);
                var definition = requireDefinition(command);
                if (longTermMemoryEnabled(invocation, definition)
                        && definition.memoryStrategy().writeScopes().contains("PERSONAL")) {
                    memoryGovernance.learn(
                            command.memorySubject(),
                            command.input(),
                            completedReply(agentEvents),
                            Instant.now());
                }
                events.add(
                        taskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.VALIDATION_COMPLETED,
                                task,
                                decision.reason(),
                                eventAgentId(route),
                                null));
                events.add(
                        taskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.EXECUTION_COMPLETED,
                                task,
                                decision.reason(),
                                eventAgentId(route),
                                null));
            }
            case CONTINUE_REPAIR -> {
                task = verifying(command, task, decision, sequence, events, eventAgentId(route));
                task =
                        transition(
                                command,
                                task,
                                TaskStatus.PAUSED,
                                decision.reason(),
                                assistantOwner(command),
                                decision.recoveryPoint());
                events.add(
                        taskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.VALIDATION_FAILED,
                                task,
                                decision.reason(),
                                eventAgentId(route),
                                null));
                events.add(
                        taskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.EXECUTION_PAUSED,
                                task,
                                decision.reason(),
                                eventAgentId(route),
                                null));
            }
            case NEEDS_USER -> {
                var awaitingAuthorization =
                        agentEvents.stream()
                                .anyMatch(
                                        event ->
                                                event.status()
                                                        == ExecutionEventStatus
                                                                .AWAITING_AUTHORIZATION);
                task =
                        transition(
                                command,
                                task,
                                awaitingAuthorization
                                        ? TaskStatus.AWAITING_AUTHORIZATION
                                        : TaskStatus.AWAITING_INPUT,
                                decision.reason(),
                                humanOwner(command),
                                decision.recoveryPoint());
                events.add(
                        taskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.VALIDATION_FAILED,
                                task,
                                decision.reason(),
                                eventAgentId(route),
                                null));
                events.add(
                        taskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.EXECUTION_PAUSED,
                                task,
                                decision.reason(),
                                eventAgentId(route),
                                null));
            }
            case FAILED -> {
                task =
                        transition(
                                command,
                                task,
                                TaskStatus.FAILED,
                                decision.reason(),
                                assistantOwner(command),
                                decision.recoveryPoint());
                events.add(
                        taskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.EXECUTION_FAILED,
                                task,
                                decision.reason(),
                                eventAgentId(route),
                                null));
            }
            case HANDOFF -> {
                task =
                        transition(
                                command,
                                task,
                                TaskStatus.PAUSED,
                                decision.reason(),
                                humanOwner(command),
                                decision.recoveryPoint());
                events.add(
                        taskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.OWNERSHIP_TRANSFERRED,
                                task,
                                decision.reason(),
                                eventAgentId(route),
                                null));
                events.add(
                        taskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.EXECUTION_PAUSED,
                                task,
                                decision.reason(),
                                eventAgentId(route),
                                null));
            }
        }
        taskRef.set(task);
        return Flux.fromIterable(events);
    }

    private AssistantTask verifying(
            AssistantCommand command,
            AssistantTask task,
            CompletionDecision decision,
            AtomicLong sequence,
            List<ExecutionEvent> events,
            AgentId agentId) {
        var verifying =
                transition(
                        command,
                        task,
                        TaskStatus.VERIFYING,
                        "验证 Agent 结果",
                        assistantOwner(command),
                        decision.recoveryPoint());
        events.add(
                taskEvent(
                        command,
                        sequence.incrementAndGet(),
                        ExecutionEventType.TASK_STATUS_CHANGED,
                        verifying,
                        "验证 Agent 结果",
                        agentId,
                        null));
        return verifying;
    }

    private AssistantTask transition(
            AssistantCommand command,
            AssistantTask current,
            TaskStatus next,
            String reason,
            TaskOwner owner,
            RecoveryPoint recoveryPoint) {
        var changed =
                current.transitionTo(
                        next, reason, assistantActor(command), owner, recoveryPoint, Instant.now());
        return tasks.save(command.tenantId(), changed, command.lease());
    }

    private Flux<ExecutionEvent> failTask(
            AssistantCommand command,
            AtomicReference<AssistantTask> taskRef,
            boolean taskProgressed,
            long sequence,
            Throwable failure) {
        var tracked = taskRef.get();
        if (!command.operation().executesAgent()
                || !taskProgressed
                || tracked == null
                || failure instanceof TaskNotResumableException) {
            return Flux.just(commandRejectedEvent(command, sequence, failure));
        }
        var current = tasks.find(command.tenantId(), command.taskId()).orElse(tracked);
        if (current.status() == TaskStatus.COMPLETED || current.status() == TaskStatus.CANCELED) {
            return Flux.just(commandRejectedEvent(command, sequence, failure));
        }
        var failed = current;
        if (current.status() != TaskStatus.FAILED) {
            failed =
                    current.transitionTo(
                            TaskStatus.FAILED,
                            "Assistant 路由、策略或执行失败",
                            assistantActor(command),
                            assistantOwner(command),
                            current.recoveryPoint(),
                            Instant.now());
            failed = tasks.save(command.tenantId(), failed, command.lease());
        }
        taskRef.set(failed);
        return Flux.just(
                taskEvent(
                        command,
                        sequence,
                        ExecutionEventType.EXECUTION_FAILED,
                        failed,
                        "Assistant 路由、策略或执行失败",
                        null,
                        null));
    }

    private static String completedReply(List<ExecutionEvent> events) {
        return events.stream()
                .filter(event -> event.type() == ExecutionEventType.MESSAGE_COMPLETED)
                .map(event -> event.payload().values().get("text"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .reduce((first, second) -> second)
                .orElse("");
    }

    private static void requireResumable(AssistantTask task) {
        if (task.status() == TaskStatus.RUNNING
                || task.status() == TaskStatus.COMPLETED
                || task.status() == TaskStatus.CANCELED) {
            throw new TaskNotResumableException("任务当前不能恢复: " + task.status());
        }
    }

    private static final class TaskNotResumableException extends IllegalStateException {
        private TaskNotResumableException(String message) {
            super(message);
        }
    }

    private AssistantTask requireTask(AssistantCommand command) {
        return tasks.find(command.tenantId(), command.taskId())
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Assistant 任务不存在: " + command.taskId().value()));
    }

    private AgentExecutionCommand agentCommand(
            AssistantInvocation invocation,
            SkillRoute route,
            AssistantDefinition definition,
            long sequenceBase,
            List<AgentMessage> memoryMessages) {
        var command = invocation.command();
        var effectiveRole = definition.roleFor(route);
        var skillSystemPromptAppendix =
                mergeSkillPrompts(effectiveSkillResolver.resolve(effectiveRole, route.skillKey()));
        var roleAllowedToolNames = effectiveRoleToolNames(command, route, effectiveRole.toolKeys());
        var authorizationRules = new LinkedHashMap<String, ToolAuthorizationRule>();
        definition
                .toolPolicy()
                .rules()
                .forEach(
                        (toolKey, rule) ->
                                authorizationRules.put(
                                        toolKey,
                                        new ToolAuthorizationRule(
                                                switch (rule.effect()) {
                                                    case READ, GENERATED_CONTENT, HUMAN_HANDOFF ->
                                                            true;
                                                    case REVERSIBLE_WRITE, IRREVERSIBLE_WRITE ->
                                                            false;
                                                },
                                                rule.reversible(),
                                                rule.authorizationRequired())));
        var toolAuthorization = new ToolAuthorizationContext(authorizationRules);
        var context =
                new InvocationContext(
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
                        toolAuthorization);
        var messages = new ArrayList<AgentMessage>();
        messages.addAll(invocation.supplementalMessages());
        messages.addAll(memoryMessages);
        messages.add(
                new AgentMessage(
                        "user:" + command.runId().value(),
                        AgentMessage.Role.USER,
                        command.input(),
                        invocation.userAttachments()));
        var executionModel =
                switch (route.subagentSpec()) {
                    case SubagentSpec.Predefined ignored -> Optional.<ModelSpec>empty();
                    case SubagentSpec.Dynamic dynamic -> {
                        var selection = command.taskModelSelection();
                        var routingContext =
                                switch (selection.mode()) {
                                    case EXPLICIT ->
                                            CapabilityRoutingContext.of(
                                                    preferenceUserId(command.userId().value()),
                                                    CapabilityRoutingContext.CAP_CHAT,
                                                    selection.modelId());
                                    case AUTO ->
                                            new CapabilityRoutingContext(
                                                    preferenceUserId(command.userId().value()),
                                                    CapabilityRoutingContext.CAP_CHAT,
                                                    null,
                                                    null,
                                                    taskFeatures(
                                                            dynamic.modelRequirement(),
                                                            command.input(),
                                                            !invocation
                                                                    .userAttachments()
                                                                    .isEmpty()));
                                };
                        var selectedModel = models.resolve(routingContext);
                        yield Optional.of(
                                new ModelSpec(
                                        Objects.requireNonNull(
                                                        selectedModel.getId(), "CHAT 模型缺少数据库主键")
                                                .toString()));
                    }
                };
        return new AgentExecutionCommand(
                route.subagentSpec(),
                Optional.of(
                        new AgentExecutionCommand.RoleAssignment(
                                effectiveRole.key(),
                                effectiveRole.name(),
                                effectiveRole.responsibilities(),
                                effectiveRole.nonResponsibilities())),
                switch (route.handlingMode()) {
                    case DIRECT -> AgentExecutionCommand.ExecutionMode.DIRECT;
                    case DELEGATE -> AgentExecutionCommand.ExecutionMode.DELEGATE;
                },
                executionModel,
                skillSystemPromptAppendix,
                roleAllowedToolNames,
                sequenceBase,
                messages,
                context);
    }

    private static Set<String> effectiveRoleToolNames(
            AssistantCommand command, SkillRoute route, Set<String> configuredToolNames) {
        if (!"system.assistant.default-user".equals(command.assistantId().value())
                || !"system.role.content-creator".equals(route.roleKey())) {
            return configuredToolNames;
        }
        return configuredToolNames.stream()
                .filter("knowledge.search"::equals)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Long preferenceUserId(String value) {
        try {
            var userId = Long.parseLong(value);
            return userId > 0 ? userId : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Map<String, Object> taskFeatures(
            ModelSelectionRequirement requirement, String input, boolean hasImage) {
        var features = new LinkedHashMap<String, Object>();
        features.put(
                CapabilityRoutingContext.FEATURE_REASONING_REQUIRED,
                requirement.reasoningRequired());
        features.put(CapabilityRoutingContext.FEATURE_COST_SENSITIVE, requirement.costSensitive());
        features.put(
                CapabilityRoutingContext.FEATURE_INPUT_LENGTH,
                input.codePointCount(0, input.length()));
        if (hasImage) {
            features.put(CapabilityRoutingContext.FEATURE_HAS_IMAGE, true);
        }
        return Map.copyOf(features);
    }

    static String mergeSkillPrompts(List<SkillDef> skills) {
        Objects.requireNonNull(skills, "skills 不能为空");
        return skills.stream()
                .sorted(
                        Comparator.comparingInt(SkillDef::priority)
                                .reversed()
                                .thenComparing(
                                        skill ->
                                                skill.skillId() == null
                                                        ? Long.MAX_VALUE
                                                        : skill.skillId())
                                .thenComparing(SkillDef::name))
                .map(SkillDef::systemPrompt)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(prompt -> !prompt.isEmpty())
                .distinct()
                .collect(Collectors.joining("\n\n"));
    }

    private static String subagentIdentifier(SkillRoute route) {
        return route.subagentSpec().identifier();
    }

    private static AgentId eventAgentId(SkillRoute route) {
        return new AgentId(subagentIdentifier(route));
    }

    private static ExecutionEvent taskEvent(
            AssistantCommand command,
            long sequence,
            ExecutionEventType type,
            AssistantTask task,
            String reason,
            AgentId agentId,
            EffectiveContextManifest manifest) {
        var values = new LinkedHashMap<String, Object>();
        values.put("reason", reason);
        values.put("taskStatus", task.status().name());
        values.put("ownerKind", task.owner().kind().name());
        values.put("ownerId", task.owner().ownerId());
        if (task.recoveryPoint() != null) {
            values.put("recoveryKey", task.recoveryPoint().key());
            values.put("recoveryDescription", task.recoveryPoint().description());
        }
        if (manifest != null) {
            values.put("skillKey", manifest.skillKey());
            values.put(
                    "contextSources",
                    manifest.sources().stream()
                            .map(
                                    source ->
                                            java.util.Map.<String, Object>of(
                                                    "type", source.type().name(),
                                                    "sourceKey", source.sourceKey(),
                                                    "version", source.version(),
                                                    "scope", source.scope(),
                                                    "reason", source.reason(),
                                                    "summary", source.summary(),
                                                    "userManageable", source.userManageable()))
                            .toList());
        }
        return event(
                command,
                sequence,
                type,
                status(task.status()),
                agentId,
                new ExecutionEventPayload(values));
    }

    private static ExecutionEvent commandRejectedEvent(
            AssistantCommand command, long sequence, Throwable failure) {
        var payload =
                new ExecutionEventPayload(
                        java.util.Map.of(
                                "errorType", failure.getClass().getSimpleName(),
                                "reason",
                                        Objects.requireNonNullElse(failure.getMessage(), "命令不合法")));
        return event(
                command,
                sequence,
                ExecutionEventType.COMMAND_REJECTED,
                ExecutionEventStatus.REJECTED,
                null,
                payload);
    }

    private static ExecutionEvent event(
            AssistantCommand command,
            long sequence,
            ExecutionEventType type,
            ExecutionEventStatus status,
            AgentId agentId,
            ExecutionEventPayload payload) {
        return new ExecutionEvent(
                new EventId(UUID.randomUUID().toString().replace("-", "")),
                command.tenantId(),
                command.conversationId(),
                command.sessionId(),
                command.taskId(),
                command.executionId(),
                command.runId(),
                command.parentExecutionId(),
                sequence,
                type,
                status,
                command.controlMode(),
                OwnerType.ASSISTANT,
                command.assistantId(),
                agentId,
                command.userId(),
                command.correlationId(),
                command.causationId(),
                command.idempotencyKey(),
                payload,
                Instant.now());
    }

    private static ExecutionEventStatus status(TaskStatus status) {
        return switch (status) {
            case DRAFT -> ExecutionEventStatus.DRAFT;
            case PLANNING -> ExecutionEventStatus.PLANNING;
            case AWAITING_AUTHORIZATION -> ExecutionEventStatus.AWAITING_AUTHORIZATION;
            case RUNNING -> ExecutionEventStatus.RUNNING;
            case VERIFYING -> ExecutionEventStatus.VERIFYING;
            case COMPLETED -> ExecutionEventStatus.COMPLETED;
            case AWAITING_INPUT -> ExecutionEventStatus.AWAITING_INPUT;
            case PAUSED -> ExecutionEventStatus.PAUSED;
            case CANCELED -> ExecutionEventStatus.CANCELED;
            case FAILED -> ExecutionEventStatus.FAILED;
            case RECOVERING -> ExecutionEventStatus.RECOVERING;
        };
    }

    private static TaskOwner assistantOwner(AssistantCommand command) {
        return new TaskOwner(OwnerKind.ASSISTANT, command.assistantId().value());
    }

    private static TaskOwner humanOwner(AssistantCommand command) {
        return new TaskOwner(OwnerKind.HUMAN, command.userId().value());
    }

    private static TaskActor assistantActor(AssistantCommand command) {
        return new TaskActor(OwnerKind.ASSISTANT, command.assistantId().value());
    }

    private static TaskActor humanActor(AssistantCommand command) {
        return new TaskActor(OwnerKind.HUMAN, command.userId().value());
    }
}
