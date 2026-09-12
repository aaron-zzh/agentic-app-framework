package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
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

import org.springframework.beans.factory.ObjectProvider;

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
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillCatalogPort;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionDecision;
import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Execution;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionProfileSnapshot;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillActivationMode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillBinding;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillDecisionAuditEvent;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillScope;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task.Actor;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task.Owner;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task.OwnerKind;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task.RecoveryPoint;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task.Status;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.EffectiveContextPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ExecutionProfileSnapshotPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.SkillDecisionAuditPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.SystemSkillBindingPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskMaterializationPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskPlanPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskRecoveryPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskUnitOfWork;
import com.xuejiai.aaf.framework.intelligent.cognition.application.MemoryGovernanceService;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextBudgetExceededException;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextRequest;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextRequest.AgentPurpose;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextRequest.ContextBudget;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextRequest.ContextScope;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextRequest.Disclosure;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;
import com.xuejiai.aaf.framework.intelligent.cognition.port.ContextCompressionPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.L1ContextPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.SessionMemoryPort;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;
import com.xuejiai.aaf.framework.intelligent.shared.event.CanonicalExecutionEventId;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventReducer;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.event.NodeIdentity;
import com.xuejiai.aaf.framework.intelligent.shared.event.TurnEndReason;
import com.xuejiai.aaf.framework.intelligent.shared.event.TurnOutcomeAggregator;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AgentId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** P2 Assistant 唯一应用用例，不感知 Spring、JPA 或 AgentScope。 */
@Slf4j
public final class AssistantApplicationService implements AssistantCommandPort {

    private static final int RUNTIME_MAX_ITERATIONS = 10;
    private static final int RUNTIME_MAX_MODEL_RETRIES = 2;
    private static final Duration RUNTIME_TIMEOUT = Duration.ofSeconds(120);

    private final ExecutionPolicy runtimeExecutionPolicy;

    private final AssistantDefinitionPort definitions;
    private final TaskUnitOfWork tasks;
    private final TaskMaterializationPort materializations;
    private final TaskPlanPort taskBoards;
    private final RoleSelector roleSelector;
    private final SystemSkillBindingPort systemSkillBindings;
    private final SkillCatalogPort skillCatalog;
    private final EffectiveSkillResolver effectiveSkillResolver;
    private final SkillSelectionPort skillSelection;
    private final EffectiveToolResolver effectiveToolResolver;
    private final SkillDecisionAuditPort skillDecisionAudits;
    private final PromptAssembler promptAssembler;
    private final ExecutionProfileSnapshotPort executionProfiles;
    private final EffectiveContextPort effectiveContexts;
    private final L1ContextPort cognitionContexts;
    private final ContextCompressionPort contextCompression;
    private final MemoryGovernanceService memoryGovernance;
    private final AgentExecutionPort agentExecution;
    private final CapabilityRouter models;
    private final CompletionValidator completionValidator;
    private final ExecutionEventStorePort eventStore;
    private final TaskRecoveryPort recoveries;
    private final ObjectProvider<SessionMemoryPort> sessionMemories;

    public AssistantApplicationService(
            AssistantDefinitionPort definitions,
            TaskUnitOfWork tasks,
            TaskMaterializationPort materializations,
            TaskPlanPort taskBoards,
            RoleSelector roleSelector,
            SystemSkillBindingPort systemSkillBindings,
            SkillCatalogPort skillCatalog,
            EffectiveSkillResolver effectiveSkillResolver,
            SkillSelectionPort skillSelection,
            EffectiveToolResolver effectiveToolResolver,
            SkillDecisionAuditPort skillDecisionAudits,
            PromptAssembler promptAssembler,
            ExecutionProfileSnapshotPort executionProfiles,
            EffectiveContextPort effectiveContexts,
            L1ContextPort cognitionContexts,
            ContextCompressionPort contextCompression,
            MemoryGovernanceService memoryGovernance,
            AgentExecutionPort agentExecution,
            CapabilityRouter models,
            CompletionValidator completionValidator,
            ExecutionEventStorePort eventStore,
            TaskRecoveryPort recoveries,
            ObjectProvider<SessionMemoryPort> sessionMemories,
            int contextWindow) {
        this.sessionMemories = Objects.requireNonNull(sessionMemories, "sessionMemories 不能为空");
        this.runtimeExecutionPolicy =
                ExecutionPolicy.withDefaultTimeouts(
                        RUNTIME_MAX_ITERATIONS,
                        RUNTIME_MAX_MODEL_RETRIES,
                        RUNTIME_TIMEOUT,
                        contextWindow);
        this.definitions = Objects.requireNonNull(definitions, "definitions 不能为空");
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
        this.materializations = Objects.requireNonNull(materializations, "materializations 不能为空");
        this.taskBoards = Objects.requireNonNull(taskBoards, "taskBoards 不能为空");
        this.roleSelector = Objects.requireNonNull(roleSelector, "roleSelector 不能为空");
        this.systemSkillBindings =
                Objects.requireNonNull(systemSkillBindings, "systemSkillBindings 不能为空");
        this.skillCatalog = Objects.requireNonNull(skillCatalog, "skillCatalog 不能为空");
        this.effectiveSkillResolver =
                Objects.requireNonNull(effectiveSkillResolver, "effectiveSkillResolver 不能为空");
        this.skillSelection = Objects.requireNonNull(skillSelection, "skillSelection 不能为空");
        this.effectiveToolResolver =
                Objects.requireNonNull(effectiveToolResolver, "effectiveToolResolver 不能为空");
        this.skillDecisionAudits =
                Objects.requireNonNull(skillDecisionAudits, "skillDecisionAudits 不能为空");
        this.promptAssembler = Objects.requireNonNull(promptAssembler, "promptAssembler 不能为空");
        this.executionProfiles =
                Objects.requireNonNull(executionProfiles, "executionProfiles 不能为空");
        this.effectiveContexts =
                Objects.requireNonNull(effectiveContexts, "effectiveContexts 不能为空");
        this.cognitionContexts =
                Objects.requireNonNull(cognitionContexts, "cognitionContexts 不能为空");
        this.contextCompression =
                Objects.requireNonNull(contextCompression, "contextCompression 不能为空");
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
        var direct = command.taskId() == null;
        var startedAtNanos = System.nanoTime();
        log.debug(
                "[AssistantExecution] stage=invocation_received operation={} executionId={} taskId={} "
                        + "assistantId={} tenantId={} userId={} requestedSkill={} modelMode={} "
                        + "modelId={} memoryMode={} inputLength={} contextSourceCount={} "
                        + "invocationPolicy={} attachmentCount={}",
                command.operation(),
                command.executionId().value(),
                taskKey(command),
                command.assistantId().value(),
                command.tenantId().value(),
                command.userId().value(),
                invocation.requestedSkillKey(),
                command.taskModelSelection().mode(),
                command.taskModelSelection().modelId(),
                invocation.memoryMode(),
                textLength(command.input()),
                command.contextCandidates().size(),
                invocation.invocationPolicy(),
                invocation.userAttachments().size());
        var persistentTaskNode =
                command.operation() == AssistantCommand.Operation.SUBTASK
                        && command.parentExecutionId() != null
                        && command.executionContract() != null;
        if (persistentTaskNode && command.lease() == null) {
            throw new IllegalStateException("持久子执行必须由调度器注入 conversation lease");
        }
        if (!persistentTaskNode && !direct) {
            recoveries.saveCommand(command);
        }
        var failureSequence = new AtomicLong(command.sequenceBase());
        var taskRef = new AtomicReference<Task>();
        var taskProgressed = new AtomicBoolean();
        return Flux.defer(
                        () ->
                                direct
                                        ? executeDirect(invocation)
                                        : command.operation() == AssistantCommand.Operation.SUBTASK
                                                ? executeNode(invocation)
                                                : executeAgent(invocation, taskRef, taskProgressed))
                .doOnNext(event -> failureSequence.accumulateAndGet(event.sequence(), Math::max))
                .onErrorResume(
                        failure -> {
                            var trackedTask = taskRef.get();
                            log.debug(
                                    "[Assistant协调] 执行失败，已进入失败收口：executionId={}，taskId={}，assistantId={}，任务状态={}，错误类型={}",
                                    command.executionId().value(),
                                    taskKey(command),
                                    command.assistantId().value(),
                                    trackedTask == null ? null : trackedTask.status(),
                                    failure.getClass().getName());
                            return direct
                                    ? failDirect(
                                            command, failureSequence.incrementAndGet(), failure)
                                    : failTask(
                                            command,
                                            taskRef,
                                            taskProgressed.get(),
                                            failureSequence.incrementAndGet(),
                                            failure);
                        })
                .concatMap(
                        event ->
                                event.type() == ExecutionEventType.EXECUTION_PROMOTED
                                                || event.ownerType() != OwnerType.ASSISTANT
                                        ? reactor.core.publisher.Mono.just(event)
                                        : eventStore.append(event, command.lease()))
                .doOnNext(
                        event -> {
                            if (event.type() != ExecutionEventType.MESSAGE_DELTA) {
                                log.debug(
                                        "[AssistantExecution] stage=event_emitted executionId={} taskId={} "
                                                + "sequence={} eventType={} status={} ownerType={}",
                                        command.executionId().value(),
                                        taskKey(command),
                                        event.sequence(),
                                        event.type(),
                                        event.status(),
                                        event.ownerType());
                            }
                        })
                .doOnError(
                        failure ->
                                log.debug(
                                        "[Assistant协调] 事件流异常关闭：executionId={}，taskId={}，错误类型={}",
                                        command.executionId().value(),
                                        taskKey(command),
                                        failure.getClass().getName()))
                .doFinally(
                        signalType ->
                                log.debug(
                                        "[AssistantExecution] stage=invocation_closed executionId={} taskId={} "
                                                + "signal={} durationMs={}",
                                        command.executionId().value(),
                                        taskKey(command),
                                        signalType,
                                        elapsedMillis(startedAtNanos)));
    }

    private Flux<ExecutionEvent> executeNode(AssistantInvocation invocation) {
        var command = invocation.command();
        var definition = requireDefinition(command);
        definition.requireControlMode(command.controlMode());
        requirePublished(definition);
        logDefinitionResolved(command, definition);
        var profileCandidate = resolveExecutionProfile(invocation, definition);
        var context = executionContext(command, definition, profileCandidate);
        var profile = freezeCompressedProfile(invocation, profileCandidate, context.messages());
        log.debug(
                "[Assistant协调] L1 上下文已冻结：executionId={}，taskId={}，agentKind={}，来源数={}，消息数={}，摘要={}",
                command.executionId().value(),
                taskKey(command),
                command.invocationProfile().agentKind(),
                context.references().size(),
                context.messages().size(),
                context.digest());
        return agentExecution.execute(
                agentCommand(
                        invocation,
                        profile,
                        command.sequenceBase(),
                        profile.contextCompression()
                                .orElseThrow(() -> new IllegalStateException("执行画像缺少冻结上下文"))
                                .finalMessages()));
    }

    private Flux<ExecutionEvent> executeDirect(AssistantInvocation invocation) {
        var command = invocation.command();
        if (command.operation() != AssistantCommand.Operation.START
                || command.controlMode()
                        != com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent
                                .ControlMode.READ_ONLY) {
            throw new IllegalArgumentException("DIRECT execution 仅允许 READ_ONLY START");
        }
        var created =
                new Execution(
                        command.tenantId(),
                        command.userId(),
                        command.conversationId(),
                        null,
                        null,
                        null,
                        null,
                        command.executionId(),
                        command.sessionId(),
                        command.runId(),
                        command.correlationId(),
                        null,
                        null,
                        Execution.Scope.DIRECT,
                        1,
                        command.executionId().value(),
                        Execution.Status.CREATED,
                        Execution.PromotionState.ELIGIBLE,
                        0,
                        assistantOwner(command),
                        0,
                        command.requestedAt(),
                        command.requestedAt());
        return Mono.fromCallable(
                        () -> {
                            tasks.createExecution(created, command);
                            return tasks.updateExecution(
                                    command.tenantId(),
                                    command.executionId(),
                                    Execution.Status.RUNNING,
                                    Instant.now());
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(ignored -> executeDirectAgent(invocation));
    }

    private Flux<ExecutionEvent> executeDirectAgent(AssistantInvocation invocation) {
        var command = invocation.command();
        var sequence = new AtomicLong(command.sequenceBase());
        var emitted = new ArrayList<ExecutionEvent>();
        emitted.add(
                event(
                        command,
                        sequence.incrementAndGet(),
                        ExecutionEventType.EXECUTION_STARTED,
                        ExecutionEventStatus.RUNNING,
                        null,
                        new ExecutionEventPayload(Map.of("scope", Execution.Scope.DIRECT.name()))));
        var definition = requireDefinition(command);
        definition.requireControlMode(command.controlMode());
        requirePublished(definition);
        logDefinitionResolved(command, definition);
        var profileCandidate = resolveExecutionProfile(invocation, definition);
        emitted.add(
                roleResolvedEvent(
                        command,
                        sequence.incrementAndGet(),
                        ExecutionEventStatus.RUNNING,
                        profileCandidate));
        var controlledContext = executionContext(command, definition, profileCandidate);
        var profile =
                freezeCompressedProfile(invocation, profileCandidate, controlledContext.messages());
        var agentEvents = new ArrayList<ExecutionEvent>();
        var executionEvents =
                agentExecution
                        .execute(
                                agentCommand(
                                        invocation,
                                        profile,
                                        sequence.get(),
                                        profile.contextCompression()
                                                .orElseThrow(
                                                        () ->
                                                                new IllegalStateException(
                                                                        "执行画像缺少冻结上下文"))
                                                .finalMessages()))
                        .doOnNext(
                                event -> {
                                    agentEvents.add(event);
                                    sequence.accumulateAndGet(event.sequence(), Math::max);
                                });
        return Flux.concat(
                Flux.fromIterable(emitted),
                executionEvents,
                Flux.defer(() -> finalizeDirect(invocation, definition, agentEvents, sequence)));
    }

    private Flux<ExecutionEvent> finalizeDirect(
            AssistantInvocation invocation,
            AssistantDefinition definition,
            List<ExecutionEvent> agentEvents,
            AtomicLong sequence) {
        var command = invocation.command();
        return Mono.fromCallable(
                        () -> {
                            var current =
                                    tasks.findExecution(command.tenantId(), command.executionId())
                                            .orElseThrow(
                                                    () ->
                                                            new IllegalStateException(
                                                                    "DIRECT execution 在完成收口时不存在"));
                            if (current.status() == Execution.Status.PROMOTED) {
                                return directTerminalEvent(
                                        command,
                                        sequence.incrementAndGet(),
                                        ExecutionEventType.EXECUTION_PROMOTED,
                                        Execution.Status.PROMOTED,
                                        "DIRECT execution 已提升为 canonical Task",
                                        TurnEndReason.USER_TURN_END,
                                        "");
                            }
                            var canceled =
                                    agentEvents.stream()
                                            .anyMatch(
                                                    event ->
                                                            event.status()
                                                                    == ExecutionEventStatus
                                                                            .CANCELED);
                            var decision =
                                    canceled
                                            ? null
                                            : completionValidator.validate(
                                                    new CompletionValidator.ValidationRequest(
                                                            null,
                                                            command.completionCriteria(),
                                                            Optional.empty(),
                                                            agentEvents));
                            var completed =
                                    decision != null
                                            && decision.outcome()
                                                    == CompletionDecision.Outcome.COMPLETED;
                            var status =
                                    canceled
                                            ? Execution.Status.CANCELED
                                            : completed
                                                    ? Execution.Status.COMPLETED
                                                    : Execution.Status.FAILED;
                            tasks.updateExecution(
                                    command.tenantId(),
                                    command.executionId(),
                                    status,
                                    Instant.now());
                            var reply = completedReply(agentEvents);
                            if (completed
                                    && invocation.memoryMode()
                                            != AssistantInvocation.MemoryMode.DISABLED) {
                                appendSessionTurn(command, reply);
                            }
                            if (completed
                                    && longTermMemoryEnabled(invocation, definition)
                                    && definition
                                            .memoryStrategy()
                                            .writeScopes()
                                            .contains("PERSONAL")) {
                                memoryGovernance.learn(
                                        command.memorySubject(),
                                        command.input(),
                                        reply,
                                        Instant.now());
                            }
                            var reason =
                                    canceled
                                            ? "DIRECT execution 已取消"
                                            : completed
                                                    ? decision.reason()
                                                    : "DIRECT execution 未满足完成条件或触及持久边界";
                            var endReason =
                                    canceled
                                            ? TurnEndReason.CANCELED_BY_USER
                                            : completed
                                                    ? TurnEndReason.USER_TURN_END
                                                    : failureEndReason(agentEvents);
                            var type =
                                    canceled
                                            ? ExecutionEventType.EXECUTION_CANCELED
                                            : completed
                                                    ? ExecutionEventType.EXECUTION_COMPLETED
                                                    : ExecutionEventType.EXECUTION_FAILED;
                            return directTerminalEvent(
                                    command,
                                    sequence.incrementAndGet(),
                                    type,
                                    status,
                                    reason,
                                    endReason,
                                    reply);
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .flux();
    }

    private Flux<ExecutionEvent> failDirect(
            AssistantCommand command, long sequence, Throwable failure) {
        return Mono.fromCallable(
                        () -> {
                            var current =
                                    tasks.findExecution(command.tenantId(), command.executionId());
                            if (current.isPresent()
                                    && current.orElseThrow().status()
                                            == Execution.Status.PROMOTED) {
                                return directTerminalEvent(
                                        command,
                                        sequence,
                                        ExecutionEventType.EXECUTION_PROMOTED,
                                        Execution.Status.PROMOTED,
                                        "DIRECT execution 已提升为 canonical Task",
                                        TurnEndReason.USER_TURN_END,
                                        "");
                            }
                            if (current.isPresent() && !current.orElseThrow().terminal()) {
                                tasks.updateExecution(
                                        command.tenantId(),
                                        command.executionId(),
                                        Execution.Status.FAILED,
                                        Instant.now());
                            }
                            return directTerminalEvent(
                                    command,
                                    sequence,
                                    current.isEmpty()
                                            ? ExecutionEventType.COMMAND_REJECTED
                                            : ExecutionEventType.EXECUTION_FAILED,
                                    Execution.Status.FAILED,
                                    "DIRECT execution 执行失败",
                                    throwableEndReason(failure),
                                    "");
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .flux();
    }

    private static ExecutionEvent directTerminalEvent(
            AssistantCommand command,
            long sequence,
            ExecutionEventType type,
            Execution.Status status,
            String reason,
            TurnEndReason endReason,
            String reply) {
        var values = new LinkedHashMap<String, Object>();
        values.put("scope", Execution.Scope.DIRECT.name());
        values.put("executionStatus", status.name());
        values.put("reason", reason);
        values.put("turnEndReason", endReason.name());
        values.put("resultLength", textLength(reply));
        if (type == ExecutionEventType.EXECUTION_PROMOTED) {
            values.put(
                    "promotedTaskId",
                    TaskCommandService.promotionTaskId(command.tenantId(), command.executionId())
                            .value());
            values.put("taskStatus", Task.Status.READY.name());
            return new ExecutionEvent(
                    CanonicalExecutionEventId.of(
                            command.tenantId(), command.executionId(), "execution-promoted"),
                    command.tenantId(),
                    command.conversationId(),
                    command.sessionId(),
                    null,
                    command.executionId(),
                    command.runId(),
                    null,
                    sequence,
                    type,
                    ExecutionEventStatus.COMPLETED,
                    command.controlMode(),
                    OwnerType.ASSISTANT,
                    command.assistantId(),
                    null,
                    null,
                    command.correlationId(),
                    null,
                    command.idempotencyKey(),
                    new ExecutionEventPayload(values),
                    Instant.now(),
                    null);
        }
        return event(
                command,
                sequence,
                type,
                status == Execution.Status.COMPLETED || status == Execution.Status.PROMOTED
                        ? ExecutionEventStatus.COMPLETED
                        : status == Execution.Status.CANCELED
                                ? ExecutionEventStatus.CANCELED
                                : ExecutionEventStatus.FAILED,
                null,
                new ExecutionEventPayload(values));
    }

    private com.xuejiai.aaf.framework.intelligent.cognition.model.ControlledContextSnapshot
            executionContext(
                    AssistantCommand command,
                    AssistantDefinition definition,
                    ExecutionProfileSnapshot profile) {
        var agentKind = command.invocationProfile().agentKind();
        var coordinator = agentKind == InvocationProfile.AgentKind.COORDINATOR;
        var aggregator = agentKind == InvocationProfile.AgentKind.AGGREGATOR;
        var summaryOnly = coordinator || aggregator;
        var scopes = EnumSet.noneOf(ContextScope.class);
        var memoryEnabled =
                profile.longTermMemoryEnabled()
                        || (summaryOnly
                                && definition.memoryStrategy().longTermEnabled()
                                && command.memorySubject().kind() == SubjectKind.USER);
        if (memoryEnabled) {
            scopes.add(ContextScope.MEMORY);
        }
        var contextPlan = command.invocationProfile().contextPlan();
        if (contextPlan.knowledgeQuery().enabled()) {
            scopes.add(ContextScope.KNOWLEDGE);
        }
        if (!contextPlan.taskMaterials().isEmpty()) {
            scopes.add(ContextScope.TASK_MATERIAL);
        }
        if (scopes.isEmpty()) {
            return com.xuejiai.aaf.framework.intelligent.cognition.model.ControlledContextSnapshot
                    .empty(command.requestedAt());
        }
        var purpose =
                coordinator
                        ? AgentPurpose.COORDINATION
                        : aggregator ? AgentPurpose.AGGREGATION : AgentPurpose.EXECUTION;
        var disclosure = summaryOnly ? Disclosure.SUMMARY_ONLY : Disclosure.CONTENT_ALLOWED;
        var maxItems = profile.contextDisclosurePolicy().maxSources();
        return cognitionContexts.resolve(
                new ContextRequest(
                        command.tenantId(),
                        command.userId(),
                        command.taskId(),
                        command.executionId(),
                        command.assistantId(),
                        command.memorySubject(),
                        purpose,
                        command.input(),
                        scopes,
                        new ContextBudget(maxItems, summaryOnly ? 1_024 : 8_192),
                        disclosure,
                        command.contextCandidates(),
                        contextPlan.taskMaterials(),
                        contextPlan.knowledgeQuery(),
                        command.conversationId().value(),
                        command.requestedAt()));
    }

    /** 写入本轮短期会话上下文；失败只记录，不影响已完成的执行。 */
    private void appendSessionTurn(AssistantCommand command, String reply) {
        var sessions = sessionMemories.getIfAvailable();
        if (sessions == null) {
            return;
        }
        try {
            sessions.appendTurn(
                    command.tenantId(),
                    command.userId(),
                    command.conversationId().value(),
                    command.input(),
                    reply);
        } catch (RuntimeException exception) {
            log.warn("[会话上下文] 短期会话写入失败，不影响本轮结果：{}", exception.getMessage());
        }
    }

    private Flux<ExecutionEvent> executeAgent(
            AssistantInvocation invocation,
            AtomicReference<Task> taskRef,
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
            requireResumable(task, command);
        }

        var definition = requireDefinition(command);
        definition.requireControlMode(command.controlMode());
        requirePublished(definition);
        logDefinitionResolved(command, definition);
        if (command.operation() == AssistantCommand.Operation.RESUME
                && command.nodeIdentity() == null) {
            task = prepareTask(command, sequence, emitted, taskRef, taskProgressed);
            taskRef.set(task);
        }
        var profileCandidate = resolveExecutionProfile(invocation, definition);
        emitted.add(roleResolvedEvent(command, sequence.incrementAndGet(), task, profileCandidate));

        var controlledContext = executionContext(command, definition, profileCandidate);
        var profile =
                freezeCompressedProfile(invocation, profileCandidate, controlledContext.messages());
        var contextCandidates = new ArrayList<>(command.contextCandidates());
        contextCandidates.addAll(controlledContext.references());
        var manifest =
                effectiveContexts.resolve(
                        command.tenantId(),
                        command.userId(),
                        profile,
                        task,
                        contextCandidates,
                        command.requestedAt());
        log.debug(
                "[Assistant协调] L1 混合上下文已决策：executionId={}，taskId={}，上下文源数={}，L1引用数={}，L1消息数={}，摘要={}",
                command.executionId().value(),
                taskKey(command),
                manifest.sources().size(),
                controlledContext.references().size(),
                controlledContext.messages().size(),
                controlledContext.digest());
        task = moveToRunning(command, task, sequence, emitted, profile, manifest);
        taskRef.set(task);
        taskProgressed.set(true);
        var agentEvents = new ArrayList<ExecutionEvent>();
        var agentCommand =
                agentCommand(
                        invocation,
                        profile,
                        sequence.get(),
                        profile.contextCompression()
                                .orElseThrow(() -> new IllegalStateException("执行画像缺少冻结上下文"))
                                .finalMessages());
        log.debug(
                "[Assistant协调] 启动 AgentLoop：executionId={}，taskId={}，角色={}，技能={}，执行模式={}，模型={}，有效工具数={}，事件序号基线={}",
                command.executionId().value(),
                taskKey(command),
                profile.roleAssignment().roleKey(),
                profile.skillExecutionProfile().activatedSkills().stream()
                        .map(skill -> skill.code())
                        .sorted()
                        .toList(),
                profile.executionMode(),
                profile.executionModel().map(ModelSpec::modelId).orElse(null),
                profile.skillExecutionProfile().effectiveTools().size(),
                sequence.get());

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
        var definition =
                definitions
                        .findById(command.tenantId(), command.assistantId())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Assistant 定义不存在: "
                                                        + command.assistantId().value()));
        requireExecutableBy(command, definition);
        return definition;
    }

    private static void requireExecutableBy(
            AssistantCommand command, AssistantDefinition definition) {
        if (definition.ownership() == AssistantDefinition.TemplateOwnership.SYSTEM_MANAGED) {
            return;
        }
        if (!command.userId().value().equals(definition.maintainer())) {
            throw new IllegalStateException("当前用户无权执行 Assistant 定义");
        }
    }

    private static void requirePublished(AssistantDefinition definition) {
        if (definition.lifecycle() != AssistantDefinition.Lifecycle.PUBLISHED) {
            throw new IllegalStateException("Assistant 定义不可执行: " + definition.lifecycle());
        }
    }

    private static void logDefinitionResolved(
            AssistantCommand command, AssistantDefinition definition) {
        log.debug(
                "[AssistantExecution] stage=assistant_definition_resolved executionId={} taskId={} "
                        + "assistantId={} assistantRevision={} lifecycle={}",
                command.executionId().value(),
                taskKey(command),
                command.assistantId().value(),
                definition.version().value(),
                definition.lifecycle());
    }

    private static String taskKey(AssistantCommand command) {
        return command.taskId() == null ? null : command.taskId().value();
    }

    private static int textLength(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    private static long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private Task prepareTask(
            AssistantCommand command,
            AtomicLong sequence,
            List<ExecutionEvent> emitted,
            AtomicReference<Task> taskRef,
            AtomicBoolean taskProgressed) {
        var task = requireTask(command);
        if (command.operation() == AssistantCommand.Operation.RESUME) {
            requireResumable(task, command);
        }
        if (task.controlMode() != command.controlMode()) {
            task =
                    task.changeControlMode(
                            command.controlMode(),
                            "用户显式切换控制模式",
                            humanActor(command),
                            command.requestedAt());
            task = tasks.saveRoot(task, command.lease());
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
        if (task.status() == Status.READY
                || task.status() == Status.DRAFT
                || task.status() == Status.PAUSED
                || task.status() == Status.FAILED) {
            task =
                    task.transitionTo(
                            Status.PLANNING,
                            command.operation() == AssistantCommand.Operation.START
                                    ? "Task Owner Assistant 开始规划"
                                    : "从持久恢复点重新规划",
                            assistantActor(command),
                            assistantOwner(command),
                            new RecoveryPoint("planning", "从 Role/Skill 选择前恢复"),
                            command.requestedAt());
            task = tasks.saveRoot(task, command.lease());
        }
        taskRef.set(task);
        taskProgressed.set(true);
        emitted.add(
                taskEvent(
                        command,
                        sequence.incrementAndGet(),
                        command.operation() == AssistantCommand.Operation.START
                                ? ExecutionEventType.EXECUTION_STARTED
                                : ExecutionEventType.RECOVERY_STARTED,
                        task,
                        command.operation() == AssistantCommand.Operation.START
                                ? "canonical Task 已进入规划"
                                : "从持久恢复点恢复 Task",
                        null,
                        null));
        return task;
    }

    private Task moveToRunning(
            AssistantCommand command,
            Task task,
            AtomicLong sequence,
            List<ExecutionEvent> emitted,
            ExecutionProfileSnapshot profile,
            EffectiveContextManifest manifest) {
        if (command.nodeIdentity() != null) {
            return task;
        }
        var agentOwner = new Owner(OwnerKind.AGENT, profile.executionSpec().identifier());
        var running =
                task.status() == Status.RUNNING
                        ? task.withRuntime(
                                Status.RUNNING,
                                agentOwner,
                                task.budgetUsage(),
                                task.checkpoint(),
                                new RecoveryPoint("agent-execution", "从 Agent 状态槽位和执行画像恢复"),
                                command.requestedAt())
                        : task.transitionTo(
                                Status.RUNNING,
                                "执行画像已冻结，交给 Agent 执行",
                                assistantActor(command),
                                agentOwner,
                                new RecoveryPoint("agent-execution", "从 Agent 状态槽位和执行画像恢复"),
                                command.requestedAt());
        tasks.saveRoot(running, command.lease());
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
            AtomicReference<Task> taskRef,
            ExecutionProfileSnapshot profile,
            List<ExecutionEvent> agentEvents,
            AtomicLong sequence) {
        var command = invocation.command();
        var agentId = new AgentId(profile.executionSpec().identifier());
        var pausedEvent =
                agentEvents.stream()
                        .filter(event -> event.type() == ExecutionEventType.EXECUTION_PAUSED)
                        .findFirst()
                        .orElse(null);
        if (pausedEvent != null) {
            var latest = tasks.findTask(command.tenantId(), command.taskId()).orElse(taskRef.get());
            if (latest.status() == Status.PAUSING) {
                var requestId =
                        Objects.toString(
                                latest.checkpoint().annotations().get("pauseRequestId"), "");
                if (requestId.isBlank()) {
                    throw new IllegalStateException("PAUSING Task 缺少 pauseRequestId");
                }
                var values = pausedEvent.payload().values();
                var savedAt = Objects.toString(values.get("pauseStateSavedAt"), "");
                materializations.acknowledgePause(
                        new TaskMaterializationPort.PauseAckCommand(
                                command.tenantId(),
                                command.taskId(),
                                requestId,
                                command.executionId(),
                                Boolean.TRUE.equals(values.get("pauseStateSaved")),
                                Objects.toString(values.get("pauseStateSlotId"), null),
                                Objects.toString(values.get("pauseStateSchema"), null),
                                savedAt.isBlank() ? null : Instant.parse(savedAt),
                                Objects.toString(values.get("pauseStateFailure"), null),
                                pausedEvent.createdAt()));
                return Flux.empty();
            }
        }
        var canceled =
                agentEvents.stream()
                        .anyMatch(event -> event.status() == ExecutionEventStatus.CANCELED);
        if (canceled) {
            var latest = tasks.findTask(command.tenantId(), command.taskId()).orElse(taskRef.get());
            if (latest.status() == Status.CANCELING
                    || latest.status() == Status.CANCELED
                    || latest.status() == Status.PAUSED) {
                return Flux.empty();
            }
            throw new IllegalStateException("Agent 取消事件缺少 durable CANCELING 请求");
        }
        if (command.nodeIdentity() != null) {
            return finalizeNodeExecution(invocation, taskRef, profile, agentEvents, sequence);
        }
        var decision =
                completionValidator.validate(
                        new CompletionValidator.ValidationRequest(
                                taskRef.get(),
                                command.completionCriteria(),
                                taskBoards.find(command.tenantId(), command.taskId()),
                                agentEvents));
        log.debug(
                "[Assistant协调] 完成条件已判定：executionId={}，taskId={}，条件类别={}，要求事件={}，要求证据字段={}，结果={}，Agent事件数={}",
                command.executionId().value(),
                taskKey(command),
                command.completionCriteria().kind(),
                command.completionCriteria().requiredEventTypes(),
                command.completionCriteria().requiredPayloadValues().keySet(),
                decision.outcome(),
                agentEvents.size());
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
                                Status.COMPLETED,
                                decision.reason(),
                                assistantOwner(command),
                                null);
                var definition = requireDefinition(command);
                var reply = completedReply(agentEvents);
                // 短期会话上下文：只受 MemoryMode 控制，与长期沉淀的写 scope 无关
                if (invocation.memoryMode() != AssistantInvocation.MemoryMode.DISABLED) {
                    appendSessionTurn(command, reply);
                }
                if (longTermMemoryEnabled(invocation, definition)
                        && definition.memoryStrategy().writeScopes().contains("PERSONAL")) {
                    memoryGovernance.learn(
                            command.memorySubject(), command.input(), reply, Instant.now());
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
                        terminalTaskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.EXECUTION_COMPLETED,
                                task,
                                decision.reason(),
                                agentId,
                                TurnEndReason.BUSINESS_COMPLETED,
                                agentEvents));
            }
            case CONTINUE_REPAIR -> {
                task = verifying(command, task, decision, sequence, events, agentId);
                task =
                        transition(
                                command,
                                task,
                                Status.PAUSED,
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
                        terminalTaskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.EXECUTION_PAUSED,
                                task,
                                decision.reason(),
                                agentId,
                                TurnEndReason.REPAIR_SCHEDULED,
                                agentEvents));
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
                                        ? Status.AWAITING_AUTHORIZATION
                                        : Status.AWAITING_CLARIFICATION,
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
                        terminalTaskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.EXECUTION_PAUSED,
                                task,
                                decision.reason(),
                                agentId,
                                awaitingAuthorization
                                        ? TurnEndReason.AUTHORIZATION_NEEDED
                                        : TurnEndReason.CLARIFICATION_NEEDED,
                                agentEvents));
            }
            case FAILED -> {
                task =
                        transition(
                                command,
                                task,
                                Status.FAILED,
                                decision.reason(),
                                assistantOwner(command),
                                decision.recoveryPoint());
                events.add(
                        terminalTaskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.EXECUTION_FAILED,
                                task,
                                decision.reason(),
                                agentId,
                                failureEndReason(agentEvents),
                                agentEvents));
            }
            case HANDOFF -> {
                task =
                        transition(
                                command,
                                task,
                                Status.PAUSED,
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
                        terminalTaskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.EXECUTION_PAUSED,
                                task,
                                decision.reason(),
                                agentId,
                                TurnEndReason.HANDOFF,
                                agentEvents));
            }
        }
        taskRef.set(task);
        return Flux.fromIterable(events);
    }

    private Flux<ExecutionEvent> finalizeNodeExecution(
            AssistantInvocation invocation,
            AtomicReference<Task> taskRef,
            ExecutionProfileSnapshot profile,
            List<ExecutionEvent> agentEvents,
            AtomicLong sequence) {
        var command = invocation.command();
        var task = taskRef.get();
        var successfulRun =
                agentEvents.stream()
                                .anyMatch(event -> event.type() == ExecutionEventType.RUN_COMPLETED)
                        && agentEvents.stream()
                                .noneMatch(
                                        event ->
                                                event.status() == ExecutionEventStatus.FAILED
                                                        || event.status()
                                                                == ExecutionEventStatus.PAUSED
                                                        || event.status()
                                                                == ExecutionEventStatus
                                                                        .AWAITING_AUTHORIZATION
                                                        || event.status()
                                                                == ExecutionEventStatus
                                                                        .AWAITING_CLARIFICATION);
        var validationPlan =
                command.nodeIdentity().kind() == NodeIdentity.NodeKind.AGGREGATOR && successfulRun
                        ? taskBoards
                                .find(command.tenantId(), command.taskId())
                                .map(
                                        plan ->
                                                plan.complete(
                                                        command.nodeIdentity().nodeId(),
                                                        completedReply(agentEvents)))
                        : Optional.<TaskPlan>empty();
        var decision =
                completionValidator.validate(
                        new CompletionValidator.ValidationRequest(
                                task, command.completionCriteria(), validationPlan, agentEvents));
        var agentId = new AgentId(profile.executionSpec().identifier());
        var events = new ArrayList<ExecutionEvent>();
        events.add(
                taskEvent(
                        command,
                        sequence.incrementAndGet(),
                        ExecutionEventType.VALIDATION_STARTED,
                        task,
                        "开始验证 TaskNode 显式业务完成条件",
                        agentId,
                        null));
        switch (decision.outcome()) {
            case COMPLETED -> {
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
                        terminalTaskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.EXECUTION_COMPLETED,
                                task,
                                decision.reason(),
                                agentId,
                                TurnEndReason.BUSINESS_COMPLETED,
                                agentEvents));
            }
            case CONTINUE_REPAIR -> {
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
                        terminalTaskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.EXECUTION_FAILED,
                                task,
                                decision.reason(),
                                agentId,
                                TurnEndReason.REPAIR_SCHEDULED,
                                agentEvents));
            }
            case NEEDS_USER -> {
                var authorization =
                        agentEvents.stream()
                                .anyMatch(
                                        event ->
                                                event.status()
                                                        == ExecutionEventStatus
                                                                .AWAITING_AUTHORIZATION);
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
                        terminalTaskEvent(
                                command,
                                sequence.incrementAndGet(),
                                ExecutionEventType.EXECUTION_PAUSED,
                                task,
                                decision.reason(),
                                agentId,
                                authorization
                                        ? TurnEndReason.AUTHORIZATION_NEEDED
                                        : TurnEndReason.CLARIFICATION_NEEDED,
                                agentEvents));
            }
            case FAILED ->
                    events.add(
                            terminalTaskEvent(
                                    command,
                                    sequence.incrementAndGet(),
                                    ExecutionEventType.EXECUTION_FAILED,
                                    task,
                                    decision.reason(),
                                    agentId,
                                    failureEndReason(agentEvents),
                                    agentEvents));
            case HANDOFF ->
                    events.add(
                            terminalTaskEvent(
                                    command,
                                    sequence.incrementAndGet(),
                                    ExecutionEventType.EXECUTION_PAUSED,
                                    task,
                                    decision.reason(),
                                    agentId,
                                    TurnEndReason.HANDOFF,
                                    agentEvents));
        }
        return Flux.fromIterable(events);
    }

    private Task verifying(
            AssistantCommand command,
            Task task,
            CompletionDecision decision,
            AtomicLong sequence,
            List<ExecutionEvent> events,
            AgentId agentId) {
        var verifying =
                transition(
                        command,
                        task,
                        Status.VERIFYING,
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

    private Task transition(
            AssistantCommand command,
            Task current,
            Status next,
            String reason,
            Owner owner,
            RecoveryPoint recoveryPoint) {
        var changed =
                current.transitionTo(
                        next, reason, assistantActor(command), owner, recoveryPoint, Instant.now());
        return tasks.saveRoot(changed, command.lease());
    }

    private Flux<ExecutionEvent> failTask(
            AssistantCommand command,
            AtomicReference<Task> taskRef,
            boolean taskProgressed,
            long sequence,
            Throwable failure) {
        var tracked = taskRef.get();
        if (!taskProgressed || tracked == null || failure instanceof TaskNotResumableException) {
            return Flux.just(commandRejectedEvent(command, sequence, failure));
        }
        var current = tasks.findTask(command.tenantId(), command.taskId()).orElse(tracked);
        if (command.nodeIdentity() != null) {
            return Flux.just(
                    terminalTaskEvent(
                            command,
                            sequence,
                            ExecutionEventType.EXECUTION_FAILED,
                            current,
                            "TaskNode Role/Skill 选择、策略或执行失败",
                            null,
                            throwableEndReason(failure),
                            List.of()));
        }
        if (current.status() == Status.COMPLETED || current.status() == Status.CANCELED) {
            return Flux.just(commandRejectedEvent(command, sequence, failure));
        }
        var failed = current;
        if (current.status() != Status.FAILED) {
            failed =
                    current.transitionTo(
                            Status.FAILED,
                            "Assistant Role/Skill 选择、策略或执行失败",
                            assistantActor(command),
                            assistantOwner(command),
                            current.recoveryPoint(),
                            Instant.now());
            failed = tasks.saveRoot(failed, command.lease());
        }
        taskRef.set(failed);
        return Flux.just(
                terminalTaskEvent(
                        command,
                        sequence,
                        ExecutionEventType.EXECUTION_FAILED,
                        failed,
                        "Assistant Role/Skill 选择、策略或执行失败",
                        null,
                        throwableEndReason(failure),
                        List.of()));
    }

    /** 从异常类型判定回合停止原因；无法归类时如实记为内部错误，不猜测。 */
    private static TurnEndReason throwableEndReason(Throwable failure) {
        return failure instanceof ContextBudgetExceededException
                ? TurnEndReason.CONTEXT_EXCEEDED
                : TurnEndReason.INTERNAL_ERROR;
    }

    /** 从已观察事件判定失败原因；模型失败优先于工具失败，均无则记为内部错误。 */
    private static TurnEndReason failureEndReason(List<ExecutionEvent> agentEvents) {
        var modelFailed = false;
        var toolDenied = false;
        for (var event : agentEvents) {
            switch (event.type()) {
                case MODEL_CALL_FAILED -> modelFailed = true;
                case TOOL_CALL_FAILED, AUTHORIZATION_DENIED -> toolDenied = true;
                default -> {
                    // 其余事件不参与失败归因。
                }
            }
        }
        if (modelFailed) {
            return TurnEndReason.MODEL_ERROR;
        }
        return toolDenied ? TurnEndReason.TOOL_DENIED : TurnEndReason.INTERNAL_ERROR;
    }

    private static String completedReply(List<ExecutionEvent> events) {
        return ExecutionEventReducer.reduce(events).resultText();
    }

    private static void requireResumable(Task task, AssistantCommand command) {
        if ((task.status() == Status.RUNNING && command.lease() == null)
                || task.status() == Status.COMPLETED
                || task.status() == Status.CANCELED) {
            throw new TaskNotResumableException("任务当前不能恢复: " + task.status());
        }
    }

    private static final class TaskNotResumableException extends IllegalStateException {
        private TaskNotResumableException(String message) {
            super(message);
        }
    }

    private Task requireTask(AssistantCommand command) {
        return tasks.findTask(command.tenantId(), command.taskId())
                .orElseThrow(
                        () -> new IllegalArgumentException("Assistant 任务不存在: " + taskKey(command)));
    }

    private ExecutionProfileSnapshot resolveExecutionProfile(
            AssistantInvocation invocation, AssistantDefinition definition) {
        var command = invocation.command();
        var expectedRevision = command.invocationProfile().expectedAssistantRevision();
        if (expectedRevision != null && expectedRevision != definition.version().value()) {
            throw new IllegalStateException("Team 成员 Assistant revision 已失效");
        }
        var existing = executionProfiles.find(command.tenantId(), command.executionId());
        if (existing.isPresent()) {
            var snapshot = existing.orElseThrow();
            if (!Objects.equals(snapshot.taskId(), command.taskId())
                    || !snapshot.assistantId().equals(command.assistantId())) {
                throw new IllegalStateException("ExecutionProfileSnapshot 与恢复命令边界不一致");
            }
            if (snapshot.invocationPolicy() != invocation.invocationPolicy()) {
                throw new IllegalStateException("已冻结 InvocationPolicy 与恢复命令不一致");
            }
            // skillExecutionProfile / compiledSystemPrompt / toolAuthorizationRules /
            // contextCompression 是随物理调用推进的可变量（PerCallProfile），此处取的已是该
            // execution 最新一条记录的真实状态，不再要求与当次请求路由或历史冻结值完全一致——
            // 那种比较只在"整条执行只冻结一次"的旧模型下才有意义。
            log.debug(
                    "[AssistantExecution] stage=execution_profile_reused executionId={} taskId={} "
                            + "roleKey={} skillCodes={} modelId={}",
                    command.executionId().value(),
                    taskKey(command),
                    snapshot.roleAssignment().roleKey(),
                    snapshot.skillExecutionProfile().activatedSkills().stream()
                            .map(skill -> skill.code())
                            .sorted()
                            .toList(),
                    snapshot.executionModel().map(ModelSpec::modelId).orElse(null));
            return snapshot;
        }

        var intent = invocation.executionIntent();
        var route = intent.resolvedRoute();
        if (route != null && route.assistantRevision() != definition.version().value()) {
            throw new IllegalStateException("FIXED Route 的 Assistant revision 已失效");
        }
        var systemBindings =
                SkillBinding.copyOf(systemSkillBindings.findEnabled(), "systemSkillBindings");
        var systemOnDemandSkillKeys =
                SkillBinding.skillKeys(systemBindings, SkillActivationMode.ON_DEMAND);
        final RoleSelector.RoleSelection roleSelection;
        if (intent.routeConstraint()
                == com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent
                        .RouteConstraint.FIXED) {
            if (!Objects.equals(invocation.requestedSkillKey(), route.skillKey())) {
                throw new IllegalArgumentException("请求 Skill 与 FIXED Route 不一致");
            }
            var fixedRole =
                    definition.roles().stream()
                            .filter(candidate -> candidate.key().equals(route.roleKey()))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "FIXED Route 的 Role 不属于 Assistant: "
                                                            + route.roleKey()));
            roleSelection =
                    new RoleSelector.RoleSelection(
                            fixedRole, "SERVER_FIXED_ROUTE", "使用服务端已解析的不可变 Route");
        } else {
            roleSelection =
                    roleSelector.select(
                            new RoleSelector.RoleSelectionRequest(
                                    definition,
                                    systemOnDemandSkillKeys,
                                    command.input(),
                                    invocation.requestedSkillKey(),
                                    command.userId()));
        }
        var role = definition.requireRole(roleSelection.role().key());
        if (!role.equals(roleSelection.role())) {
            throw new IllegalStateException("RoleSelector 返回内容与 Assistant 已发布候选不一致");
        }
        appendRoleAudit(command, roleSelection);
        var scopedBindings =
                scopedSkillBindings(
                        systemBindings, definition.assistantSkillBindings(), role.skillBindings());
        var onDemandBindings =
                scopedBindings.stream()
                        .filter(
                                binding ->
                                        binding.binding().activationMode()
                                                == SkillActivationMode.ON_DEMAND)
                        .toList();
        var selectionBindings = onDemandBindings;
        // route.skillKey()==null 表示只锁角色、技能仍开放（AAF-107 #10708）：候选集合保持角色下全部
        // ON_DEMAND Skill，不做精确匹配收窄；只有技能也被锁定时才要求候选集合精确命中该技能。
        if (route != null && route.skillKey() != null) {
            selectionBindings =
                    onDemandBindings.stream()
                            .filter(
                                    binding ->
                                            binding.binding().skillKey().equals(route.skillKey()))
                            .toList();
            if (selectionBindings.size() != 1) {
                throw new IllegalStateException("FIXED Route 必须精确引用当前 Scope 的 ON_DEMAND Skill");
            }
        }
        // 选择阶段只暴露摘要；正文仅在最终激活集合确定后加载，后续由模型上下文预算统一治理。
        var selectionCandidates =
                selectionBindings.stream().map(this::authorizedSkillSummary).toList();
        var selectionMode =
                route != null && route.skillKey() != null
                        ? com.xuejiai.aaf.framework.intelligent.assistant.model.SkillSelectionMode
                                .FIXED
                        : com.xuejiai.aaf.framework.intelligent.assistant.model.SkillSelectionMode
                                .SELECT_AND_AUGMENT;
        var selection =
                new com.xuejiai.aaf.framework.intelligent.agent.model.SkillSelectionManifest(
                        role.key(),
                        route == null ? null : route.skillKey(),
                        selectionMode,
                        selectionCandidates.size(),
                        selectionCandidates);
        appendSelectionAudit(
                command,
                role,
                selection,
                "SKILL_SELECTION_REQUESTED",
                null,
                roleSelection.selectedBy(),
                "选择阶段仅使用 SYSTEM、ASSISTANT 与所选 ROLE 的 ON_DEMAND 摘要");
        var decision =
                route != null
                        ? new SkillSelectionPort.SkillSelectionDecision(
                                route.skillKey() == null ? List.of() : List.of(route.skillKey()),
                                "SERVER_FIXED_ROUTE",
                                route.skillKey() == null
                                        ? "服务端已解析 Role 且未选定 ON_DEMAND Skill"
                                        : "使用服务端固定 ON_DEMAND Skill")
                        : selectionCandidates.isEmpty()
                                ? new SkillSelectionPort.SkillSelectionDecision(
                                        List.of(),
                                        "NO_ON_DEMAND_CANDIDATE",
                                        "无 ON_DEMAND 候选，不调用选择模型")
                                : skillSelection.select(
                                        new SkillSelectionPort.SelectionRequest(
                                                selection,
                                                command.input(),
                                                invocation.requestedSkillKey(),
                                                command.userId()));
        var selectedOnDemandKeys = selectedSkillKeys(selection, decision);
        var finalBindings =
                scopedBindings.stream()
                        .filter(
                                binding ->
                                        binding.binding().activationMode()
                                                        == SkillActivationMode.ALWAYS
                                                || selectedOnDemandKeys.contains(
                                                        binding.binding().skillKey()))
                        .toList();
        var authorizedSkillKeys =
                scopedBindings.stream()
                        .map(binding -> binding.binding().skillKey())
                        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        var requestedSkillKeys =
                finalBindings.stream()
                        .map(binding -> binding.binding().skillKey())
                        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        var resolvedSkills =
                effectiveSkillResolver.resolve(authorizedSkillKeys, requestedSkillKeys);
        var activatedSkills = activateSkills(finalBindings, resolvedSkills);
        log.debug(
                "[AssistantExecution] stage=role_resolved executionId={} taskId={} roleKey={} "
                        + "selectedBy={} onDemandCandidateCount={} requestedSkill={}",
                command.executionId().value(),
                taskKey(command),
                role.key(),
                roleSelection.selectedBy(),
                selectionCandidates.size(),
                invocation.requestedSkillKey());
        activatedSkills.forEach(
                skill ->
                        appendSelectionAudit(
                                command,
                                role,
                                selection,
                                skill.activationMode() == SkillActivationMode.ALWAYS
                                        ? "SKILL_AUTO_ACTIVATED"
                                        : "SKILL_SELECTED",
                                skill.code(),
                                skill.activationMode() == SkillActivationMode.ALWAYS
                                        ? "AAF_BINDING"
                                        : decision.selectedBy(),
                                skill.activationMode() == SkillActivationMode.ALWAYS
                                        ? "绑定声明 ALWAYS，自动激活但仍执行工具授权"
                                        : decision.reason()));
        var activatedSystemSkills =
                activatedSkills.stream()
                        .filter(skill -> skill.scope() == SkillScope.SYSTEM)
                        .toList();
        var activatedAssistantSkills =
                activatedSkills.stream()
                        .filter(skill -> skill.scope() == SkillScope.ASSISTANT)
                        .toList();
        var activatedRoleSkills =
                activatedSkills.stream().filter(skill -> skill.scope() == SkillScope.ROLE).toList();
        if (activatedSystemSkills.stream()
                .anyMatch(skill -> !skill.requiredToolNames().isEmpty())) {
            throw new IllegalStateException("SYSTEM Skill 初版禁止声明工具要求");
        }
        var roleSkillRequirements =
                activatedRoleSkills.stream()
                        .flatMap(skill -> skill.requiredToolNames().stream())
                        .collect(Collectors.toUnmodifiableSet());
        var roleInheritRoleTools =
                activatedRoleSkills.stream()
                        .anyMatch(
                                com.xuejiai.aaf.framework.intelligent.agent.model.ActivatedSkill
                                        ::inheritRoleTools);
        var assistantSkillRequirements =
                activatedAssistantSkills.stream()
                        .flatMap(skill -> skill.requiredToolNames().stream())
                        .collect(Collectors.toUnmodifiableSet());
        var assistantInheritRoleTools =
                activatedAssistantSkills.stream()
                        .anyMatch(
                                com.xuejiai.aaf.framework.intelligent.agent.model.ActivatedSkill
                                        ::inheritRoleTools);
        var roleAllowedToolNames = allowedToolNames(role.toolKeys(), command, definition, "Role");
        var assistantAllowedToolNames =
                allowedToolNames(definition.assistantToolKeys(), command, definition, "Assistant");
        var coordinator =
                command.invocationProfile().agentKind() == InvocationProfile.AgentKind.COORDINATOR;
        var aggregator =
                command.invocationProfile().agentKind() == InvocationProfile.AgentKind.AGGREGATOR;
        var internalPlanner = coordinator || aggregator;
        var declaredSkillRequirements =
                java.util.stream.Stream.concat(
                                roleSkillRequirements.stream(), assistantSkillRequirements.stream())
                        .collect(Collectors.toUnmodifiableSet());
        var allowedToolNames =
                java.util.stream.Stream.concat(
                                roleAllowedToolNames.stream(), assistantAllowedToolNames.stream())
                        .collect(Collectors.toUnmodifiableSet());
        if (intent.autoSaveDraft() && !internalPlanner) {
            var saveTool = intent.artifactPolicy().saveTool();
            if (!declaredSkillRequirements.contains(saveTool)
                    || !allowedToolNames.contains(saveTool)) {
                throw new IllegalStateException("产物保存工具不在 Skill 固定执行画像内: " + saveTool);
            }
        }
        var agentAllowedTools = toolRefs(allowedToolNames);
        var roleAgentAllowedTools =
                roleAllowedToolNames.isEmpty() ? List.<ToolRef>of() : agentAllowedTools;
        var roleEffectiveTools =
                effectiveToolResolver.resolve(
                        roleSkillRequirements,
                        roleInheritRoleTools,
                        roleAllowedToolNames,
                        roleAgentAllowedTools);
        var assistantEffectiveTools =
                activatedAssistantSkills.isEmpty()
                        ? List.<ToolRef>of()
                        : effectiveToolResolver.resolveAssistant(
                                assistantSkillRequirements,
                                assistantInheritRoleTools,
                                assistantAllowedToolNames,
                                agentAllowedTools);
        var resolvedTools = mergeEffectiveTools(roleEffectiveTools, assistantEffectiveTools);
        var effectiveTools =
                mergeEffectiveTools(
                        internalPlanner ? List.<ToolRef>of() : resolvedTools,
                        builtinControlTools(command));
        log.debug(
                "[AssistantExecution] stage=skill_resolved executionId={} taskId={} selectionMode={} "
                        + "skillCodes={} skillVersions={} selectedBy={} effectiveToolCount={}",
                command.executionId().value(),
                taskKey(command),
                selection.selectionMode(),
                activatedSkills.stream().map(skill -> skill.code()).toList(),
                activatedSkills.stream().map(skill -> skill.version().toString()).toList(),
                decision.selectedBy(),
                effectiveTools.size());
        var authorizationRules = new LinkedHashMap<String, ToolAuthorizationRule>();
        effectiveTools.forEach(
                tool -> {
                    if (builtinControlTool(tool.name())) {
                        authorizationRules.put(
                                tool.name(),
                                new ToolAuthorizationRule(
                                        true,
                                        false,
                                        false,
                                        ToolAuthorizationContext.MissingGrantBehavior
                                                .REQUEST_ON_DEMAND));
                        return;
                    }
                    definition.toolPolicy().requireAllowed(command.controlMode(), tool.name());
                    var rule = definition.toolPolicy().rules().get(tool.name());
                    var actionPolicy = intent.actionAuthorizationPolicy();
                    var configuredBehavior = actionPolicy.behavior(tool.name());
                    var missingGrantBehavior =
                            switch (configuredBehavior) {
                                case DEFAULT, REQUEST_ON_DEMAND ->
                                        ToolAuthorizationContext.MissingGrantBehavior
                                                .REQUEST_ON_DEMAND;
                                case PREAUTHORIZED_ONLY ->
                                        ToolAuthorizationContext.MissingGrantBehavior
                                                .PREAUTHORIZED_ONLY;
                                case DENY -> ToolAuthorizationContext.MissingGrantBehavior.DENY;
                            };
                    authorizationRules.put(
                            tool.name(),
                            new ToolAuthorizationRule(
                                    switch (rule.effect()) {
                                        case READ, GENERATED_CONTENT, HUMAN_HANDOFF -> true;
                                        case REVERSIBLE_WRITE, IRREVERSIBLE_WRITE -> false;
                                    },
                                    rule.reversible(),
                                    rule.authorizationRequired()
                                            || actionPolicy.requiresAuthorization(tool.name()),
                                    missingGrantBehavior));
                });
        var skillExecutionProfile =
                new com.xuejiai.aaf.framework.intelligent.agent.model.SkillExecutionProfile(
                        selection, activatedSkills, effectiveTools);
        var modelRequirement = runtimeModelRequirement(activatedSkills);
        var selectedModel =
                models.resolve(routingContext(command, invocation, definition, modelRequirement));
        log.debug(
                "[AssistantExecution] stage=model_resolved executionId={} taskId={} modelMode={} "
                        + "requestedModelId={} selectedModelId={}",
                command.executionId().value(),
                taskKey(command),
                command.taskModelSelection().mode(),
                command.taskModelSelection().modelId(),
                selectedModel.getId());
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
        var executionSpec =
                runtimeExecutionSpec(command, definition, role, effectiveTools, modelRequirement);
        var executionMode =
                internalPlanner
                                || command.invocationProfile().agentKind()
                                        == InvocationProfile.AgentKind.EXECUTOR
                                || !role.key().equals(definition.defaultRoleKey())
                        ? AgentExecutionCommand.ExecutionMode.DELEGATE
                        : AgentExecutionCommand.ExecutionMode.DIRECT;
        var compiledSystemPrompt =
                promptAssembler.compileAssistant(
                        new PromptAssembler.AssistantPromptRequest(
                                definition,
                                executionSpec,
                                roleAssignment,
                                skillExecutionProfile,
                                invocation.invocationPolicy(),
                                intent,
                                command.completionCriteria(),
                                command.executionId()));
        var snapshot =
                new ExecutionProfileSnapshot(
                        command.tenantId(),
                        command.taskId(),
                        command.executionId(),
                        command.assistantId(),
                        definition.version().value(),
                        intent,
                        switch (command.invocationProfile().agentKind()) {
                            case COORDINATOR ->
                                    com.xuejiai.aaf.framework.intelligent.assistant.model
                                            .ContextDisclosurePolicy.coordinatorSummary();
                            case AGGREGATOR ->
                                    com.xuejiai.aaf.framework.intelligent.assistant.model
                                            .ContextDisclosurePolicy.aggregatorSummary();
                            case EXECUTOR ->
                                    com.xuejiai.aaf.framework.intelligent.assistant.model
                                            .ContextDisclosurePolicy.executorMinimal();
                            case PRIMARY ->
                                    com.xuejiai.aaf.framework.intelligent.assistant.model
                                            .ContextDisclosurePolicy.primaryMinimal();
                        },
                        executionSpec,
                        roleAssignment,
                        executionMode,
                        executionModel,
                        invocation.invocationPolicy(),
                        longTermMemoryEnabled(invocation, definition),
                        invocation.userAttachments(),
                        command.requestedAt(),
                        new ExecutionProfileSnapshot.PerCallProfile(
                                skillExecutionProfile,
                                compiledSystemPrompt,
                                authorizationRules,
                                Optional.empty()));
        log.debug(
                "[Assistant协调] Agent 画像候选已决策：executionId={}，taskId={}，agentKind={}，agentKey={}，模型模式={}，assistantRevision={}，roleKey={}，执行模式={}，技能数={}，有效工具数={}，模型={}",
                command.executionId().value(),
                taskKey(command),
                command.invocationProfile().agentKind(),
                command.invocationProfile().agentKey(),
                command.taskModelSelection().mode(),
                snapshot.assistantRevision(),
                snapshot.roleAssignment().roleKey(),
                snapshot.executionMode(),
                snapshot.skillExecutionProfile().activatedSkills().size(),
                snapshot.skillExecutionProfile().effectiveTools().size(),
                snapshot.executionModel().map(ModelSpec::modelId).orElse(null));
        return snapshot;
    }

    private ExecutionProfileSnapshot freezeCompressedProfile(
            AssistantInvocation invocation,
            ExecutionProfileSnapshot profile,
            List<AgentMessage> controlledContextMessages) {
        var command = invocation.command();
        var rawMessages = rawAgentMessages(invocation, profile, controlledContextMessages);
        var compression =
                contextCompression.compress(
                        new ContextCompressionPort.CompressionRequest(
                                profile.compiledSystemPrompt().content(),
                                rawMessages,
                                "user:" + command.runId().value(),
                                profile.executionModel()
                                        .orElseThrow(
                                                () ->
                                                        new IllegalStateException(
                                                                "Harness 上下文压缩缺少冻结模型")),
                                preferenceUserId(command.userId().value())),
                        profile.contextCompression());
        var newlyFrozen = profile.contextCompression().isEmpty();
        var frozen = executionProfiles.freeze(profile.withContextCompression(compression));
        if (newlyFrozen) {
            auditExecutionProfile(command, frozen, "CONTEXT_COMPRESSION");
        }
        log.debug(
                "[Assistant协调] Agent 画像与上下文已冻结：executionId={}，策略={}，原因={}，原Token={}，最终Token={}，AI摘要={}，最终消息数={}",
                command.executionId().value(),
                compression.policy().version(),
                compression.triggerReasons(),
                compression.originalEstimatedTokens(),
                compression.finalEstimatedTokens(),
                compression.aiSummaryUsed(),
                compression.finalMessages().size());
        return frozen;
    }

    private static List<AgentMessage> rawAgentMessages(
            AssistantInvocation invocation,
            ExecutionProfileSnapshot profile,
            List<AgentMessage> controlledContextMessages) {
        var command = invocation.command();
        var messages = new ArrayList<AgentMessage>();
        messages.addAll(controlledContextMessages);
        messages.add(
                new AgentMessage(
                        "user:" + command.runId().value(),
                        AgentMessage.Role.USER,
                        command.input(),
                        profile.userAttachments()));
        return List.copyOf(messages);
    }

    private AgentExecutionCommand agentCommand(
            AssistantInvocation invocation,
            ExecutionProfileSnapshot profile,
            long sequenceBase,
            List<AgentMessage> finalMessages) {
        var command = invocation.command();
        var stateSlotId =
                command.taskId() == null
                        ? command.executionId().value()
                        : tasks.findExecution(command.tenantId(), command.executionId())
                                .map(Execution::stateSlotId)
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "DELEGATED Agent 调用缺少 canonical Execution"));
        var context =
                new InvocationContext(
                        command.tenantId(),
                        command.userId(),
                        profile.executionIntent().workspaceId(),
                        command.assistantId(),
                        command.conversationId(),
                        command.sessionId(),
                        command.taskId(),
                        command.executionId(),
                        stateSlotId,
                        command.runId(),
                        command.parentExecutionId(),
                        command.correlationId(),
                        command.causationId(),
                        command.idempotencyKey(),
                        command.controlMode(),
                        command.executionContract(),
                        command.lease(),
                        new ToolAuthorizationContext(profile.toolAuthorizationRules()),
                        command.nodeIdentity(),
                        command.dispatchId(),
                        command.dispatchGeneration(),
                        command.dispatchFencingToken(),
                        command.dispatchLeaseOwner(),
                        command.dispatchLeaseUntil());
        log.debug(
                "[Assistant协调] Agent 上下文已拼装：executionId={}，promptSha256={}，最终压缩消息数={}，用户附件数={}，草稿策略={}",
                command.executionId().value(),
                profile.compiledSystemPrompt().sha256(),
                finalMessages.size(),
                profile.userAttachments().size(),
                profile.executionIntent().artifactPolicy().persistenceMode());
        return new AgentExecutionCommand(
                profile.executionSpec(),
                Optional.of(profile.roleAssignment()),
                profile.executionMode(),
                profile.executionModel(),
                profile.skillExecutionProfile(),
                profile.compiledSystemPrompt(),
                sequenceBase,
                finalMessages,
                context,
                command.resumeStateRequired());
    }

    private record ScopedSkillBinding(SkillScope scope, SkillBinding binding) {
        private ScopedSkillBinding {
            Objects.requireNonNull(scope, "scope 不能为空");
            Objects.requireNonNull(binding, "binding 不能为空");
        }
    }

    private static List<ScopedSkillBinding> scopedSkillBindings(
            List<SkillBinding> systemBindings,
            List<SkillBinding> assistantBindings,
            List<SkillBinding> roleBindings) {
        var result = new ArrayList<ScopedSkillBinding>();
        systemBindings.forEach(
                binding -> result.add(new ScopedSkillBinding(SkillScope.SYSTEM, binding)));
        assistantBindings.forEach(
                binding -> result.add(new ScopedSkillBinding(SkillScope.ASSISTANT, binding)));
        roleBindings.forEach(
                binding -> result.add(new ScopedSkillBinding(SkillScope.ROLE, binding)));
        var owners = new LinkedHashMap<String, SkillScope>();
        result.forEach(
                binding -> {
                    var existing =
                            owners.putIfAbsent(binding.binding().skillKey(), binding.scope());
                    if (existing != null) {
                        throw new IllegalStateException(
                                "Skill 不能跨 Scope 重复绑定: "
                                        + binding.binding().skillKey()
                                        + " ("
                                        + existing
                                        + "/"
                                        + binding.scope()
                                        + ")");
                    }
                });
        return List.copyOf(result);
    }

    private com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizedSkillSummary
            authorizedSkillSummary(ScopedSkillBinding scopedBinding) {
        var summary =
                skillCatalog
                        .findSummaryByCode(scopedBinding.binding().skillKey())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "不存在 APPROVED Skill: "
                                                        + scopedBinding.binding().skillKey()));
        return new com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizedSkillSummary(
                summary.code(),
                summary.name(),
                summary.summary(),
                scopedBinding.scope(),
                scopedBinding.binding().activationMode(),
                List.of(),
                summary.requiredModelCapabilities(),
                summary.requiredToolNames());
    }

    private static Set<String> selectedSkillKeys(
            com.xuejiai.aaf.framework.intelligent.agent.model.SkillSelectionManifest selection,
            SkillSelectionPort.SkillSelectionDecision decision) {
        var selected = new java.util.LinkedHashSet<String>();
        for (var code : decision.selectedSkillKeys()) {
            if (code == null || code.isBlank() || !selected.add(code)) {
                throw new IllegalArgumentException("Skill 选择结果包含空值或重复值");
            }
        }
        var candidates =
                selection.candidates().stream()
                        .map(candidate -> candidate.code())
                        .collect(Collectors.toUnmodifiableSet());
        if (!candidates.containsAll(selected) || selected.size() > selection.maxActivatedSkills()) {
            throw new IllegalArgumentException("Skill 选择结果超出 ON_DEMAND 候选范围");
        }
        if (selection.selectionMode()
                        == com.xuejiai.aaf.framework.intelligent.assistant.model.SkillSelectionMode
                                .FIXED
                && (selected.size() != 1 || !selected.contains(selection.defaultSkillKey()))) {
            throw new IllegalArgumentException("FIXED 必须精确选择目标 ON_DEMAND Skill");
        }
        return java.util.Collections.unmodifiableSet(selected);
    }

    private static List<com.xuejiai.aaf.framework.intelligent.agent.model.ActivatedSkill>
            activateSkills(List<ScopedSkillBinding> bindings, List<SkillDef> resolvedSkills) {
        var byCode =
                resolvedSkills.stream()
                        .collect(
                                Collectors.toMap(
                                        SkillDef::code,
                                        skill -> skill,
                                        (left, right) -> {
                                            throw new IllegalStateException(
                                                    "Skill 目录返回重复 code: " + left.code());
                                        },
                                        LinkedHashMap::new));
        var activated =
                bindings.stream()
                        .map(
                                binding -> {
                                    var skill = byCode.get(binding.binding().skillKey());
                                    if (skill == null) {
                                        throw new IllegalArgumentException(
                                                "最终激活 Skill 未解析到 APPROVED 版本: "
                                                        + binding.binding().skillKey());
                                    }
                                    if (binding.scope() == SkillScope.SYSTEM
                                            && !skill.requiredToolNames().isEmpty()) {
                                        throw new IllegalStateException(
                                                "SYSTEM Skill 初版禁止声明工具要求: " + skill.code());
                                    }
                                    return new com.xuejiai.aaf.framework.intelligent.agent.model
                                            .ActivatedSkill(
                                            skill.code(),
                                            skill.version(),
                                            binding.scope(),
                                            binding.binding().activationMode(),
                                            skill.content(),
                                            skill.requiredToolNames(),
                                            skill.requiredModelCapabilities(),
                                            List.of(),
                                            List.of(),
                                            skill.inheritRoleTools());
                                })
                        .toList();
        if (activated.size() != resolvedSkills.size()) {
            throw new IllegalStateException("Skill 目录解析结果与最终激活绑定不一致");
        }
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
                        taskKey(command),
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
                        taskKey(command),
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
                                                taskKey(command),
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
                        taskKey(command),
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

    private static Set<String> allowedToolNames(
            Set<String> configuredToolNames,
            AssistantCommand command,
            AssistantDefinition definition,
            String owner) {
        return configuredToolNames.stream()
                .sorted()
                .filter(
                        toolName ->
                                !command.invocationProfile().toolScopeRestricted()
                                        || command.invocationProfile()
                                                .allowedToolKeys()
                                                .contains(toolName))
                .filter(
                        toolName -> {
                            var rule =
                                    Objects.requireNonNull(
                                            definition.toolPolicy().rules().get(toolName),
                                            owner + " 工具缺少 Assistant ToolPolicy: " + toolName);
                            if (!definition
                                    .toolPolicy()
                                    .allows(command.controlMode(), rule.effect())) {
                                return false;
                            }
                            return command.controlMode() != ExecutionEvent.ControlMode.COLLABORATIVE
                                    || rule.effect()
                                            != com.xuejiai.aaf.framework.intelligent.assistant.model
                                                    .ToolPolicy.ActionEffect.REVERSIBLE_WRITE
                                    || rule.reversible();
                        })
                .collect(Collectors.toUnmodifiableSet());
    }

    private static List<ToolRef> builtinControlTools(AssistantCommand command) {
        if (command.taskId() == null
                && command.invocationProfile().agentKind() == InvocationProfile.AgentKind.PRIMARY) {
            return List.of(
                    toolRef(InspectTasksTool.TOOL_NAME),
                    toolRef(PromoteDirectTaskTool.TOOL_NAME),
                    toolRef(AmendTaskTool.TOOL_NAME),
                    toolRef(PauseTaskTool.TOOL_NAME),
                    toolRef(ResumeTaskTool.TOOL_NAME),
                    toolRef(TakeOverTaskTool.TOOL_NAME),
                    toolRef(HandBackTaskTool.TOOL_NAME),
                    toolRef(CancelTaskTool.TOOL_NAME));
        }
        if (command.taskId() != null && command.nodeIdentity() != null) {
            if (command.invocationProfile().agentKind()
                    == InvocationProfile.AgentKind.COORDINATOR) {
                return List.of(
                        toolRef(SubmitCoordinationPlanTool.TOOL_NAME),
                        toolRef(RequestClarificationTool.TOOL_NAME));
            }
            return List.of(toolRef(RequestClarificationTool.TOOL_NAME));
        }
        return List.of();
    }

    private static boolean builtinControlTool(String toolName) {
        return InspectTasksTool.TOOL_NAME.equals(toolName)
                || PromoteDirectTaskTool.TOOL_NAME.equals(toolName)
                || AmendTaskTool.TOOL_NAME.equals(toolName)
                || PauseTaskTool.TOOL_NAME.equals(toolName)
                || ResumeTaskTool.TOOL_NAME.equals(toolName)
                || TakeOverTaskTool.TOOL_NAME.equals(toolName)
                || HandBackTaskTool.TOOL_NAME.equals(toolName)
                || CancelTaskTool.TOOL_NAME.equals(toolName)
                || SubmitCoordinationPlanTool.TOOL_NAME.equals(toolName)
                || RequestClarificationTool.TOOL_NAME.equals(toolName);
    }

    private static ToolRef toolRef(String toolName) {
        return new ToolRef(toolName, 1, toolName);
    }

    private static List<ToolRef> mergeEffectiveTools(
            List<ToolRef> roleTools, List<ToolRef> assistantTools) {
        var merged = new LinkedHashMap<String, ToolRef>();
        roleTools.forEach(tool -> merged.putIfAbsent(tool.name(), tool));
        assistantTools.forEach(tool -> merged.putIfAbsent(tool.name(), tool));
        return List.copyOf(merged.values());
    }

    private static List<ToolRef> toolRefs(Set<String> toolKeys) {
        return toolKeys.stream().sorted().map(toolKey -> new ToolRef(toolKey, 1, toolKey)).toList();
    }

    private static CapabilityRoutingContext routingContext(
            AssistantCommand command,
            AssistantInvocation invocation,
            AssistantDefinition definition,
            ModelSelectionRequirement requirement) {
        var modelSelection = command.taskModelSelection();
        var hasImage = !invocation.userAttachments().isEmpty();
        return switch (modelSelection.mode()) {
            case EXPLICIT ->
                    CapabilityRoutingContext.of(
                            preferenceUserId(command.userId().value()),
                            hasImage
                                    ? CapabilityRoutingContext.CAP_VISION
                                    : CapabilityRoutingContext.CAP_CHAT,
                            modelSelection.modelId());
            case AUTO ->
                    new CapabilityRoutingContext(
                            preferenceUserId(command.userId().value()),
                            CapabilityRoutingContext.CAP_CHAT,
                            null,
                            definition.modelId(),
                            taskFeatures(requirement, command.input(), hasImage));
        };
    }

    private SubagentSpec.Dynamic runtimeExecutionSpec(
            AssistantCommand command,
            AssistantDefinition definition,
            Role role,
            List<ToolRef> effectiveTools,
            ModelSelectionRequirement modelRequirement) {
        var persona = definition.persona();
        var systemPrompt =
                """
                你是 %s。
                %s
                人格：%s
                表达风格：%s
                基础约束：%s
                """
                        .formatted(
                                persona.name(),
                                persona.description(),
                                persona.personality(),
                                persona.speakingStyle(),
                                persona.instructions())
                        .trim();
        return new SubagentSpec.Dynamic(
                runtimeAgentIdentifier(command, role),
                "由 Assistant %s 的 Role %s 在本次执行中动态派生"
                        .formatted(command.assistantId().value(), role.name()),
                systemPrompt,
                effectiveTools,
                runtimeExecutionPolicy,
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

    private static String runtimeAgentIdentifier(AssistantCommand command, Role role) {
        var identity =
                command.invocationProfile().agentKind().name().toLowerCase(java.util.Locale.ROOT);
        var key = command.invocationProfile().agentKey().replaceAll("[^A-Za-z0-9._:-]", "_");
        var identifier =
                command.assistantId().value() + ':' + role.key() + ':' + identity + ':' + key;
        if (identifier.length() <= 128) return identifier;
        return "aaf-agent:"
                + java.util.UUID.nameUUIDFromBytes(
                        identifier.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static ExecutionEvent taskEvent(
            AssistantCommand command,
            long sequence,
            ExecutionEventType type,
            Task task,
            String reason,
            AgentId agentId,
            EffectiveContextManifest manifest) {
        return event(
                command,
                sequence,
                type,
                status(task.status()),
                agentId,
                new ExecutionEventPayload(taskPayloadValues(task, reason, manifest)));
    }

    /** 终态事件额外携带回合停止原因与度量收口，供恢复决策和容量分析使用。 */
    private static ExecutionEvent terminalTaskEvent(
            AssistantCommand command,
            long sequence,
            ExecutionEventType type,
            Task task,
            String reason,
            AgentId agentId,
            TurnEndReason endReason,
            List<ExecutionEvent> observedEvents) {
        var values = taskPayloadValues(task, reason, null);
        values.putAll(TurnOutcomeAggregator.aggregate(endReason, observedEvents).toPayloadValues());
        return event(
                command,
                sequence,
                type,
                status(task.status()),
                agentId,
                new ExecutionEventPayload(values));
    }

    private static LinkedHashMap<String, Object> taskPayloadValues(
            Task task, String reason, EffectiveContextManifest manifest) {
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
        return values;
    }

    /** 本次执行冻结的 Role 事实；同一任务内前后 roleKey 不同即为角色接力，履历由事件流投影得出。 */
    private static ExecutionEvent roleResolvedEvent(
            AssistantCommand command, long sequence, Task task, ExecutionProfileSnapshot profile) {
        return roleResolvedEvent(command, sequence, status(task.status()), profile);
    }

    private static ExecutionEvent roleResolvedEvent(
            AssistantCommand command,
            long sequence,
            ExecutionEventStatus eventStatus,
            ExecutionProfileSnapshot profile) {
        var payload =
                new ExecutionEventPayload(
                        java.util.Map.of(
                                "roleKey", profile.roleAssignment().roleKey(),
                                "roleName", profile.roleAssignment().roleName(),
                                "routeConstraint",
                                        profile.executionIntent().routeConstraint().name(),
                                "interactionMode",
                                        profile.executionIntent().interactionMode().name()));
        return event(
                command, sequence, ExecutionEventType.ROLE_RESOLVED, eventStatus, null, payload);
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
                Instant.now(),
                // 节点身份随命令透传：AG-UI 投影据此区分交付类节点与内部节点（修复此前缺口——本方法此前调用不带
                // nodeIdentity 的旧版 ExecutionEvent 构造器，导致 EXECUTION_STARTED/COMPLETED/FAILED/
                // CANCELED、VALIDATION_* 这批"任务级"事件即使属于子任务也恒为 null，与 AgentScopeEventMapper
                // 产生的"Agent 调用级"事件（RUN_STARTED/MESSAGE_*/TOOL_CALL_* 等）行为不一致）。
                command.nodeIdentity());
    }

    private static ExecutionEventStatus status(Status status) {
        return switch (status) {
            case DRAFT -> ExecutionEventStatus.DRAFT;
            case PLANNING -> ExecutionEventStatus.PLANNING;
            case READY -> ExecutionEventStatus.RECOVERING;
            case AWAITING_AUTHORIZATION -> ExecutionEventStatus.AWAITING_AUTHORIZATION;
            case RUNNING -> ExecutionEventStatus.RUNNING;
            case VERIFYING -> ExecutionEventStatus.VERIFYING;
            case COMPLETED -> ExecutionEventStatus.COMPLETED;
            case AWAITING_CLARIFICATION -> ExecutionEventStatus.AWAITING_CLARIFICATION;
            case PAUSED, PAUSING -> ExecutionEventStatus.PAUSED;
            case CANCELING, CANCELED -> ExecutionEventStatus.CANCELED;
            case FAILED -> ExecutionEventStatus.FAILED;
        };
    }

    private static Owner assistantOwner(AssistantCommand command) {
        return new Owner(OwnerKind.ASSISTANT, command.assistantId().value());
    }

    private static Owner humanOwner(AssistantCommand command) {
        return new Owner(OwnerKind.HUMAN, command.userId().value());
    }

    private static Actor assistantActor(AssistantCommand command) {
        return new Actor(OwnerKind.ASSISTANT, command.assistantId().value());
    }

    private static Actor humanActor(AssistantCommand command) {
        return new Actor(OwnerKind.HUMAN, command.userId().value());
    }
}
