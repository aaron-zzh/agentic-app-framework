package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import com.xuejiai.aaf.framework.intelligent.agent.model.ExecutionPolicy;
import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ModelSelectionRequirement;
import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext.ToolAuthorizationRule;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
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
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionProfileSnapshot;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillDecisionAuditEvent;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.EffectiveContextPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ExecutionProfileSnapshotPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.SkillDecisionAuditPort;
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

    private static final ExecutionPolicy RUNTIME_EXECUTION_POLICY =
            new ExecutionPolicy(10, 2, Duration.ofSeconds(120));

    private final AssistantDefinitionPort definitions;
    private final TaskControlPort tasks;
    private final TaskBoardPort taskBoards;
    private final RoleSelector roleSelector;
    private final EffectiveSkillResolver effectiveSkillResolver;
    private final SkillSelectionPort skillSelection;
    private final EffectiveToolResolver effectiveToolResolver;
    private final SkillDecisionAuditPort skillDecisionAudits;
    private final ExecutionProfileSnapshotPort executionProfiles;
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
            RoleSelector roleSelector,
            EffectiveSkillResolver effectiveSkillResolver,
            SkillSelectionPort skillSelection,
            EffectiveToolResolver effectiveToolResolver,
            SkillDecisionAuditPort skillDecisionAudits,
            ExecutionProfileSnapshotPort executionProfiles,
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
        this.roleSelector = Objects.requireNonNull(roleSelector, "roleSelector 不能为空");
        this.effectiveSkillResolver =
                Objects.requireNonNull(effectiveSkillResolver, "effectiveSkillResolver 不能为空");
        this.skillSelection = Objects.requireNonNull(skillSelection, "skillSelection 不能为空");
        this.effectiveToolResolver =
                Objects.requireNonNull(effectiveToolResolver, "effectiveToolResolver 不能为空");
        this.skillDecisionAudits =
                Objects.requireNonNull(skillDecisionAudits, "skillDecisionAudits 不能为空");
        this.executionProfiles =
                Objects.requireNonNull(executionProfiles, "executionProfiles 不能为空");
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
        var profile = resolveExecutionProfile(invocation, definition);
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
                        invocation, profile, command.sequenceBase(), memoryContext.messages()));
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
        var profile = resolveExecutionProfile(invocation, definition);

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
                        profile,
                        task,
                        contextCandidates,
                        command.requestedAt());
        task = moveToRunning(command, task, sequence, emitted, profile, manifest);
        taskRef.set(task);
        taskProgressed.set(true);
        var agentEvents = new ArrayList<ExecutionEvent>();
        var agentCommand =
                agentCommand(invocation, profile, sequence.get(), memoryContext.messages());

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
                Flux.defer(
                        () -> finalizeTask(invocation, taskRef, profile, agentEvents, sequence)));
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

    private AssistantDefinition requireDefinition(AssistantCommand command) {
        return definitions
                .findById(command.tenantId(), command.assistantId())
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Assistant 定义不存在: " + command.assistantId().value()));
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
                            new RecoveryPoint("planning", "从 Role/Skill 选择前恢复"),
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
                            new RecoveryPoint("planning", "从 Role/Skill 选择前恢复"),
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
            ExecutionProfileSnapshot profile,
            EffectiveContextManifest manifest) {
        var running =
                task.transitionTo(
                        TaskStatus.RUNNING,
                        "执行画像已冻结，交给 Agent 执行",
                        assistantActor(command),
                        new TaskOwner(OwnerKind.AGENT, profile.executionSpec().identifier()),
                        new RecoveryPoint("agent-execution", "从 Agent 状态槽位和执行画像恢复"),
                        command.requestedAt());
        tasks.save(command.tenantId(), running, command.lease());
        emitted.add(
                taskEvent(
                        command,
                        sequence.incrementAndGet(),
                        ExecutionEventType.TASK_STATUS_CHANGED,
                        running,
                        "执行画像已冻结，交给 Agent 执行",
                        new AgentId(profile.executionSpec().identifier()),
                        manifest));
        return running;
    }

    private Flux<ExecutionEvent> finalizeTask(
            AssistantInvocation invocation,
            AtomicReference<AssistantTask> taskRef,
            ExecutionProfileSnapshot profile,
            List<ExecutionEvent> agentEvents,
            AtomicLong sequence) {
        var command = invocation.command();
        var agentId = new AgentId(profile.executionSpec().identifier());
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
                            agentId,
                            null));
        }
        var decision =
                completionValidator.validate(
                        new CompletionValidator.ValidationRequest(
                                taskRef.get(),
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
                        agentId,
                        null));

        switch (decision.outcome()) {
            case COMPLETED -> {
                task = verifying(command, task, decision, sequence, events, agentId);
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
                                agentId,
                                null));
                events.add(
                        taskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.EXECUTION_COMPLETED,
                                task,
                                decision.reason(),
                                agentId,
                                null));
            }
            case CONTINUE_REPAIR -> {
                task = verifying(command, task, decision, sequence, events, agentId);
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
                                agentId,
                                null));
                events.add(
                        taskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.EXECUTION_PAUSED,
                                task,
                                decision.reason(),
                                agentId,
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
                                agentId,
                                null));
                events.add(
                        taskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.EXECUTION_PAUSED,
                                task,
                                decision.reason(),
                                agentId,
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
                                agentId,
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
                                agentId,
                                null));
                events.add(
                        taskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.EXECUTION_PAUSED,
                                task,
                                decision.reason(),
                                agentId,
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
                            "Assistant Role/Skill 选择、策略或执行失败",
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
                        "Assistant Role/Skill 选择、策略或执行失败",
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

    private ExecutionProfileSnapshot resolveExecutionProfile(
            AssistantInvocation invocation, AssistantDefinition definition) {
        var command = invocation.command();
        var existing = executionProfiles.find(command.tenantId(), command.executionId());
        if (existing.isPresent()) {
            var snapshot = existing.orElseThrow();
            if (!snapshot.taskId().equals(command.taskId())
                    || !snapshot.assistantId().equals(command.assistantId())) {
                throw new IllegalStateException("ExecutionProfileSnapshot 与恢复命令边界不一致");
            }
            return snapshot;
        }

        var roleSelection =
                roleSelector.select(
                        new RoleSelector.RoleSelectionRequest(
                                definition,
                                command.input(),
                                invocation.requestedSkillKey(),
                                command.userId()));
        var role = roleSelection.role();
        appendRoleAudit(command, roleSelection);
        var candidateSkills = effectiveSkillResolver.resolve(role, role.skillKeys());
        if (candidateSkills.isEmpty()) {
            throw new IllegalStateException("选定 Role 没有可执行的已批准 Skill: " + role.key());
        }
        var defaultSkillKey =
                invocation.requestedSkillKey() != null
                        ? invocation.requestedSkillKey()
                        : candidateSkills.stream()
                                .map(SkillDef::code)
                                .sorted()
                                .findFirst()
                                .orElseThrow();
        var selectionMode =
                candidateSkills.size() == 1
                        ? com.xuejiai.aaf.framework.intelligent.assistant.model.SkillSelectionMode
                                .FIXED
                        : com.xuejiai.aaf.framework.intelligent.assistant.model.SkillSelectionMode
                                .SELECT_AND_AUGMENT;
        var selection =
                new com.xuejiai.aaf.framework.intelligent.agent.model.SkillSelectionManifest(
                        role.key(),
                        defaultSkillKey,
                        selectionMode,
                        Math.min(2, candidateSkills.size()),
                        candidateSkills.stream()
                                .map(
                                        skill ->
                                                new com.xuejiai.aaf.framework.intelligent.agent
                                                        .model.AuthorizedSkillSummary(
                                                        skill.code(),
                                                        skill.name(),
                                                        skill.summary(),
                                                        List.of(),
                                                        skill.requiredModelCapabilities(),
                                                        skill.requiredToolNames()))
                                .toList());
        appendSelectionAudit(
                command,
                role,
                selection,
                "SKILL_SELECTION_REQUESTED",
                null,
                roleSelection.selectedBy(),
                "选择阶段仅使用选定 Role 内的候选摘要");
        var decision =
                skillSelection.select(
                        new SkillSelectionPort.SelectionRequest(
                                selection,
                                command.input(),
                                invocation.requestedSkillKey(),
                                command.userId()));
        var activatedSkills = activateSkills(candidateSkills, selection, decision);
        activatedSkills.forEach(
                skill ->
                        appendSelectionAudit(
                                command,
                                role,
                                selection,
                                "SKILL_SELECTED",
                                skill.code(),
                                decision.selectedBy(),
                                decision.reason()));
        var skillRequirements =
                activatedSkills.stream()
                        .flatMap(skill -> skill.requiredToolNames().stream())
                        .collect(Collectors.toUnmodifiableSet());
        var roleAllowedToolNames = role.toolKeys();
        var effectiveTools =
                effectiveToolResolver.resolve(
                        skillRequirements,
                        roleAllowedToolNames,
                        roleToolRefs(roleAllowedToolNames));
        var authorizationRules = new LinkedHashMap<String, ToolAuthorizationRule>();
        effectiveTools.forEach(
                tool -> {
                    definition.toolPolicy().requireAllowed(command.controlMode(), tool.name());
                    var rule = definition.toolPolicy().rules().get(tool.name());
                    authorizationRules.put(
                            tool.name(),
                            new ToolAuthorizationRule(
                                    switch (rule.effect()) {
                                        case READ, GENERATED_CONTENT, HUMAN_HANDOFF -> true;
                                        case REVERSIBLE_WRITE, IRREVERSIBLE_WRITE -> false;
                                    },
                                    rule.reversible(),
                                    rule.authorizationRequired()));
                });
        var skillExecutionProfile =
                new com.xuejiai.aaf.framework.intelligent.agent.model.SkillExecutionProfile(
                        selection, activatedSkills, effectiveTools);
        var modelRequirement = runtimeModelRequirement(activatedSkills);
        var selectedModel =
                models.resolve(routingContext(command, invocation, definition, modelRequirement));
        var executionModel =
                Optional.of(
                        new ModelSpec(
                                Objects.requireNonNull(selectedModel.getId(), "CHAT 模型缺少数据库主键")
                                        .toString()));
        var roleAssignment =
                new AgentExecutionCommand.RoleAssignment(
                        role.key(),
                        role.name(),
                        role.responsibilities(),
                        role.nonResponsibilities());
        var snapshot =
                new ExecutionProfileSnapshot(
                        command.tenantId(),
                        command.taskId(),
                        command.executionId(),
                        command.assistantId(),
                        definition.version().value(),
                        runtimeExecutionSpec(
                                command, definition, role, effectiveTools, modelRequirement),
                        roleAssignment,
                        role.key().equals(definition.defaultRoleKey())
                                ? AgentExecutionCommand.ExecutionMode.DIRECT
                                : AgentExecutionCommand.ExecutionMode.DELEGATE,
                        executionModel,
                        skillExecutionProfile,
                        authorizationRules,
                        command.requestedAt());
        var frozen = executionProfiles.freeze(snapshot);
        auditExecutionProfile(command, frozen, decision.selectedBy());
        return frozen;
    }

    private AgentExecutionCommand agentCommand(
            AssistantInvocation invocation,
            ExecutionProfileSnapshot profile,
            long sequenceBase,
            List<AgentMessage> memoryMessages) {
        var command = invocation.command();
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
                        new ToolAuthorizationContext(profile.toolAuthorizationRules()));
        var messages = new ArrayList<AgentMessage>();
        messages.addAll(invocation.supplementalMessages());
        messages.addAll(memoryMessages);
        messages.add(
                new AgentMessage(
                        "user:" + command.runId().value(),
                        AgentMessage.Role.USER,
                        command.input(),
                        invocation.userAttachments()));
        return new AgentExecutionCommand(
                profile.executionSpec(),
                Optional.of(profile.roleAssignment()),
                profile.executionMode(),
                profile.executionModel(),
                profile.skillExecutionProfile(),
                sequenceBase,
                messages,
                context);
    }

    private List<com.xuejiai.aaf.framework.intelligent.agent.model.ActivatedSkill> activateSkills(
            List<SkillDef> candidates,
            com.xuejiai.aaf.framework.intelligent.agent.model.SkillSelectionManifest selection,
            SkillSelectionPort.SkillSelectionDecision decision) {
        var selectedCodes =
                decision.selectedSkillKeys().stream()
                        .filter(code -> code != null && !code.isBlank())
                        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (selectedCodes.isEmpty() || selectedCodes.size() > selection.maxActivatedSkills()) {
            throw new IllegalArgumentException("Skill 选择结果为空或超出 Role 激活上限");
        }
        if (selection.selectionMode()
                        != com.xuejiai.aaf.framework.intelligent.assistant.model.SkillSelectionMode
                                .SELECT_AND_AUGMENT
                && selectedCodes.size() != 1) {
            throw new IllegalArgumentException("当前 Skill 选择策略仅允许激活一个 Skill");
        }
        if (selection.selectionMode()
                        == com.xuejiai.aaf.framework.intelligent.assistant.model.SkillSelectionMode
                                .FIXED
                && !selectedCodes.contains(selection.defaultSkillKey())) {
            throw new IllegalArgumentException("FIXED Role 只能激活默认 Skill");
        }
        var byCode = candidates.stream().collect(Collectors.toMap(SkillDef::code, skill -> skill));
        var activated =
                selectedCodes.stream()
                        .map(
                                code -> {
                                    var skill = byCode.get(code);
                                    if (skill == null) {
                                        throw new IllegalArgumentException(
                                                "选择结果包含 Role Scope 外 Skill: " + code);
                                    }
                                    return new com.xuejiai.aaf.framework.intelligent.agent.model
                                            .ActivatedSkill(
                                            skill.code(),
                                            skill.version(),
                                            skill.content(),
                                            skill.requiredToolNames(),
                                            skill.requiredModelCapabilities(),
                                            List.of(),
                                            List.of());
                                })
                        .toList();
        var distinctModelRequirements =
                activated.stream()
                        .map(skill -> skill.requiredModelCapabilities())
                        .filter(requirements -> !requirements.isEmpty())
                        .distinct()
                        .count();
        if (distinctModelRequirements > 1) {
            throw new IllegalStateException("多 Skill 的模型能力要求不兼容，必须拆分任务执行");
        }
        return activated;
    }

    private void appendRoleAudit(
            AssistantCommand command, RoleSelector.RoleSelection roleSelection) {
        skillDecisionAudits.append(
                new SkillDecisionAuditEvent(
                        command.tenantId().value(),
                        null,
                        null,
                        command.assistantId().value(),
                        command.conversationId().value(),
                        command.sessionId().value(),
                        command.taskId().value(),
                        command.executionId().value(),
                        command.runId().value(),
                        roleSelection.role().key(),
                        null,
                        "ROLE_SELECTED",
                        null,
                        null,
                        roleSelection.role().skillKeys().stream().sorted().toList(),
                        null,
                        roleSelection.reason(),
                        roleSelection.selectedBy(),
                        List.of(),
                        null,
                        null,
                        command.correlationId().value(),
                        Instant.now()));
    }

    private void appendSelectionAudit(
            AssistantCommand command,
            Role role,
            com.xuejiai.aaf.framework.intelligent.agent.model.SkillSelectionManifest selection,
            String eventType,
            String skillCode,
            String selectedBy,
            String reasonDetail) {
        skillDecisionAudits.append(
                new SkillDecisionAuditEvent(
                        command.tenantId().value(),
                        null,
                        null,
                        command.assistantId().value(),
                        command.conversationId().value(),
                        command.sessionId().value(),
                        command.taskId().value(),
                        command.executionId().value(),
                        command.runId().value(),
                        role.key(),
                        selection.selectionMode().name(),
                        eventType,
                        skillCode,
                        null,
                        selection.candidates().stream()
                                .map(candidate -> candidate.code())
                                .sorted()
                                .toList(),
                        null,
                        reasonDetail,
                        selectedBy,
                        List.of(),
                        null,
                        null,
                        command.correlationId().value(),
                        Instant.now()));
    }

    private void auditExecutionProfile(
            AssistantCommand command, ExecutionProfileSnapshot profile, String selectedBy) {
        var candidateCodes =
                profile.skillExecutionProfile().selection().candidates().stream()
                        .map(candidate -> candidate.code())
                        .sorted()
                        .toList();
        var effectiveToolRefs =
                profile.skillExecutionProfile().effectiveTools().stream()
                        .map(tool -> tool.toolId() + "@" + tool.version() + ":" + tool.name())
                        .toList();
        profile.skillExecutionProfile()
                .activatedSkills()
                .forEach(
                        skill ->
                                skillDecisionAudits.append(
                                        new SkillDecisionAuditEvent(
                                                command.tenantId().value(),
                                                null,
                                                null,
                                                command.assistantId().value(),
                                                command.conversationId().value(),
                                                command.sessionId().value(),
                                                command.taskId().value(),
                                                command.executionId().value(),
                                                command.runId().value(),
                                                profile.roleAssignment().roleKey(),
                                                profile.skillExecutionProfile()
                                                        .selection()
                                                        .selectionMode()
                                                        .name(),
                                                "SKILL_ACTIVATED",
                                                skill.code(),
                                                skill.version().versionId(),
                                                candidateCodes,
                                                null,
                                                "已冻结 immutable APPROVED Skill 版本",
                                                selectedBy,
                                                effectiveToolRefs,
                                                profile.executionModel()
                                                        .map(ModelSpec::modelId)
                                                        .orElse(null),
                                                null,
                                                command.correlationId().value(),
                                                profile.frozenAt())));
        skillDecisionAudits.append(
                new SkillDecisionAuditEvent(
                        command.tenantId().value(),
                        null,
                        null,
                        command.assistantId().value(),
                        command.conversationId().value(),
                        command.sessionId().value(),
                        command.taskId().value(),
                        command.executionId().value(),
                        command.runId().value(),
                        profile.roleAssignment().roleKey(),
                        profile.skillExecutionProfile().selection().selectionMode().name(),
                        "EXECUTION_PROFILE_FROZEN",
                        null,
                        null,
                        candidateCodes,
                        null,
                        "ExecutionProfileSnapshot 已持久化，可用于审计与任务恢复",
                        selectedBy,
                        effectiveToolRefs,
                        profile.executionModel().map(ModelSpec::modelId).orElse(null),
                        null,
                        command.correlationId().value(),
                        profile.frozenAt()));
    }

    private static List<ToolRef> roleToolRefs(Set<String> toolKeys) {
        return toolKeys.stream().sorted().map(toolKey -> new ToolRef(toolKey, 1, toolKey)).toList();
    }

    private static CapabilityRoutingContext routingContext(
            AssistantCommand command,
            AssistantInvocation invocation,
            AssistantDefinition definition,
            ModelSelectionRequirement requirement) {
        var modelSelection = command.taskModelSelection();
        return switch (modelSelection.mode()) {
            case EXPLICIT ->
                    CapabilityRoutingContext.of(
                            preferenceUserId(command.userId().value()),
                            CapabilityRoutingContext.CAP_CHAT,
                            modelSelection.modelId());
            case AUTO ->
                    new CapabilityRoutingContext(
                            preferenceUserId(command.userId().value()),
                            CapabilityRoutingContext.CAP_CHAT,
                            null,
                            definition.modelId(),
                            taskFeatures(
                                    requirement,
                                    command.input(),
                                    !invocation.userAttachments().isEmpty()));
        };
    }

    private static SubagentSpec.Dynamic runtimeExecutionSpec(
            AssistantCommand command,
            AssistantDefinition definition,
            Role role,
            List<ToolRef> effectiveTools,
            ModelSelectionRequirement modelRequirement) {
        var actor = definition.actor();
        var systemPrompt =
                """
                你是 %s。
                %s
                人格：%s
                表达风格：%s
                基础约束：%s
                """
                        .formatted(
                                actor.name(),
                                actor.description(),
                                actor.personality(),
                                actor.speakingStyle(),
                                actor.instructions())
                        .trim();
        return new SubagentSpec.Dynamic(
                runtimeAgentIdentifier(command, role),
                "由 Assistant %s 的 Role %s 在本次执行中动态派生"
                        .formatted(command.assistantId().value(), role.name()),
                systemPrompt,
                effectiveTools,
                RUNTIME_EXECUTION_POLICY,
                modelRequirement,
                false);
    }

    private static ModelSelectionRequirement runtimeModelRequirement(
            List<com.xuejiai.aaf.framework.intelligent.agent.model.ActivatedSkill> skills) {
        var reasoningRequired =
                skills.stream()
                        .flatMap(skill -> skill.requiredModelCapabilities().stream())
                        .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                        .anyMatch(value -> value.contains("reason"));
        return reasoningRequired
                ? ModelSelectionRequirement.reasoning()
                : ModelSelectionRequirement.balanced();
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
                .map(SkillDef::content)
                .map(String::trim)
                .filter(content -> !content.isEmpty())
                .distinct()
                .collect(Collectors.joining("\n\n"));
    }

    private static String runtimeAgentIdentifier(AssistantCommand command, Role role) {
        return command.assistantId().value() + ':' + role.key();
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
            values.put("roleKey", manifest.roleKey());
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
