package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import com.xuejiai.aaf.framework.intelligent.agent.model.PendingToolCall;
import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext.ToolAuthorizationRule;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentExecutionPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillCatalogPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillToolCatalogPort;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionDecision;
import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Execution;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent;
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
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationRolePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DomainContextPort;
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
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionLiveEventPort;
import com.xuejiai.aaf.framework.intelligent.shared.event.NodeIdentity;
import com.xuejiai.aaf.framework.intelligent.shared.event.TurnEndReason;
import com.xuejiai.aaf.framework.intelligent.shared.event.TurnOutcomeAggregator;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AgentId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;
import com.xuejiai.aaf.framework.org.OrgContext;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** P2 Assistant 唯一应用用例，不感知 Spring、JPA 或 AgentScope。 */
@Slf4j
public final class AssistantApplicationService implements AssistantCommandPort {

    private static final int RUNTIME_MAX_ITERATIONS = 10;
    private static final int RUNTIME_MAX_MODEL_RETRIES = 2;

    /** 未显式指定知识库时，助理默认库与角色库合并后的检索数量上限。 */
    private static final int MAX_DEFAULT_KNOWLEDGE_BASES = 3;

    private static final Duration RUNTIME_TIMEOUT = Duration.ofMinutes(5);

    private final ExecutionPolicy runtimeExecutionPolicy;

    private final AssistantDefinitionPort definitions;
    private final TaskUnitOfWork tasks;
    private final TaskMaterializationPort materializations;
    private final TaskPlanPort taskBoards;
    private final ObjectProvider<ConversationRolePort> conversationRoles;
    private final DirectRoleHandoffRegistry roleHandoffs;
    private final SystemSkillBindingPort systemSkillBindings;
    private final SkillCatalogPort skillCatalog;
    private final SkillToolCatalogPort skillTools;
    private final EffectiveSkillResolver effectiveSkillResolver;
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
    private final ExecutionLiveEventPort liveEvents;
    private final TaskRecoveryPort recoveries;
    private final ObjectProvider<SessionMemoryPort> sessionMemories;
    private final ToolVisibilityFilter toolVisibility;
    private final boolean executionTraceEnabled;

    public AssistantApplicationService(
            AssistantDefinitionPort definitions,
            TaskUnitOfWork tasks,
            TaskMaterializationPort materializations,
            TaskPlanPort taskBoards,
            ObjectProvider<ConversationRolePort> conversationRoles,
            DirectRoleHandoffRegistry roleHandoffs,
            SystemSkillBindingPort systemSkillBindings,
            SkillCatalogPort skillCatalog,
            SkillToolCatalogPort skillTools,
            EffectiveSkillResolver effectiveSkillResolver,
            EffectiveToolResolver effectiveToolResolver,
            ToolVisibilityFilter toolVisibility,
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
            ExecutionLiveEventPort liveEvents,
            TaskRecoveryPort recoveries,
            ObjectProvider<SessionMemoryPort> sessionMemories,
            int contextWindow,
            boolean executionTraceEnabled) {
        this.sessionMemories = Objects.requireNonNull(sessionMemories, "sessionMemories 不能为空");
        this.toolVisibility = Objects.requireNonNull(toolVisibility, "toolVisibility 不能为空");
        this.executionTraceEnabled = executionTraceEnabled;
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
        this.conversationRoles =
                Objects.requireNonNull(conversationRoles, "conversationRoles 不能为空");
        this.roleHandoffs = Objects.requireNonNull(roleHandoffs, "roleHandoffs 不能为空");
        this.systemSkillBindings =
                Objects.requireNonNull(systemSkillBindings, "systemSkillBindings 不能为空");
        this.skillCatalog = Objects.requireNonNull(skillCatalog, "skillCatalog 不能为空");
        this.skillTools = Objects.requireNonNull(skillTools, "skillTools 不能为空");
        this.effectiveSkillResolver =
                Objects.requireNonNull(effectiveSkillResolver, "effectiveSkillResolver 不能为空");
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
        this.liveEvents = Objects.requireNonNull(liveEvents, "liveEvents 不能为空");
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
                        && command.nodeIdentity() != null
                        && command.executionContract() != null;
        if (persistentTaskNode && command.lease() == null) {
            throw new IllegalStateException("持久任务节点必须由调度器注入 conversation lease");
        }
        if (!persistentTaskNode && !direct) {
            recoveries.saveCommand(command);
        }
        var failureSequence = new AtomicLong(command.sequenceBase());
        var taskRef = new AtomicReference<Task>();
        var taskProgressed = new AtomicBoolean();
        var trace = ExecutionTraceRecorder.start(executionTraceEnabled, command);
        // 派发线程没有 HTTP 请求上下文：用命令中的可信 tenantId（orgId）与冻结在执行意图里的
        // workspaceId 恢复组织上下文，覆盖同步准备阶段（定义/Skill/审计等按组织过滤的 Repository）。
        var pipeline =
                Flux.defer(
                                () ->
                                        OrgContext.runIfAbsent(
                                                orgIdOf(command),
                                                invocation.executionIntent().workspaceId(),
                                                () ->
                                                        direct
                                                                ? executeDirect(invocation, trace)
                                                                : command.operation()
                                                                                == AssistantCommand
                                                                                        .Operation
                                                                                        .SUBTASK
                                                                        ? executeNode(
                                                                                invocation, trace)
                                                                        : executeAgent(
                                                                                invocation,
                                                                                taskRef,
                                                                                taskProgressed,
                                                                                trace)))
                        .doOnNext(
                                event ->
                                        failureSequence.accumulateAndGet(
                                                event.sequence(), Math::max))
                        .onErrorResume(
                                failure -> {
                                    var trackedTask = taskRef.get();
                                    log.warn(
                                            "[Assistant协调] 执行失败，已进入失败收口：executionId={}，taskId={}，assistantId={}，任务状态={}，错误类型={}",
                                            command.executionId().value(),
                                            taskKey(command),
                                            command.assistantId().value(),
                                            trackedTask == null ? null : trackedTask.status(),
                                            failure.getClass().getName(),
                                            failure);
                                    trace.failure(failure);
                                    return direct
                                            ? failDirect(
                                                    command,
                                                    failureSequence.incrementAndGet(),
                                                    failure)
                                            : failTask(
                                                    command,
                                                    taskRef,
                                                    taskProgressed.get(),
                                                    failureSequence.incrementAndGet(),
                                                    failure);
                                })
                        .doOnNext(trace::observe)
                        .concatMap(
                                event ->
                                        shouldPersistEvent(event)
                                                ? eventStore.append(event, command.lease())
                                                : reactor.core.publisher.Mono.just(event))
                        // 瞬态增量与已落库事件统一实时发布；入口订阅方据此转发，不再依赖轮询事件表
                        .doOnNext(liveEvents::publish)
                        .doOnNext(
                                event -> {
                                    if (event.type() != ExecutionEventType.MESSAGE_DELTA
                                            && event.type() != ExecutionEventType.REASONING_DELTA
                                            && event.type()
                                                    != ExecutionEventType.TOOL_CALL_ARGS_DELTA) {
                                        log.debug(
                                                "[执行事件·框架落库] stage=event_emitted executionId={} taskId={} "
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
                                failure -> {
                                    log.warn(
                                            "[Assistant协调] 事件流异常关闭：executionId={}，taskId={}，错误类型={}",
                                            command.executionId().value(),
                                            taskKey(command),
                                            failure.getClass().getName(),
                                            failure);
                                    trace.failure(failure);
                                })
                        .doFinally(
                                signalType -> {
                                    // 实时订阅的收口由入口按交付节点与等待点判定（RunDeliveryTracker），
                                    // 单个节点执行结束不再结束整个任务的实时流
                                    var durationMs = elapsedMillis(startedAtNanos);
                                    log.debug(
                                            "[AssistantExecution] stage=invocation_closed executionId={} taskId={} "
                                                    + "signal={} durationMs={}",
                                            command.executionId().value(),
                                            taskKey(command),
                                            signalType,
                                            durationMs);
                                    trace.finish(signalType.name(), durationMs);
                                })
                        // 组织上下文必须显式写入 Reactor Context：runIfAbsent 在 subscribe 后（Flux.defer 惰性）才设置
                        // ThreadLocal，晚于 Reactor 自动 contextCapture 的捕获点，光靠 ThreadLocalAccessor
                        // 无法传播到
                        // boundedElastic 等异步线程；写入 Context 后，注册的 accessor 才能在每次跨线程调度时恢复 org。
                        .contextWrite(
                                ctx -> {
                                    var next = ctx;
                                    var orgId = orgIdOf(command);
                                    if (orgId != null) {
                                        next = next.put(OrgContext.ORG_ID_CONTEXT_KEY, orgId);
                                    }
                                    var wsId = invocation.executionIntent().workspaceId();
                                    if (wsId != null) {
                                        next = next.put(OrgContext.WORKSPACE_ID_CONTEXT_KEY, wsId);
                                    }
                                    return next;
                                });
        if (!direct) {
            return pipeline;
        }
        // 角色切换续接（ADR-011）：前一执行以 ROLE_SWITCHED 收口后，在整条管线外递归发起新角色执行，
        // 续接执行拥有独立的持久化、失败收口与追踪；未切换或被取消时只清理登记。
        var switched = new AtomicBoolean();
        var lastSequence = new AtomicLong(command.sequenceBase());
        return pipeline.doOnNext(
                        event -> {
                            lastSequence.accumulateAndGet(event.sequence(), Math::max);
                            if (event.type() == ExecutionEventType.ROLE_SWITCHED) {
                                switched.set(true);
                            }
                        })
                .concatWith(
                        Flux.defer(
                                () -> {
                                    var handoff = roleHandoffs.take(command.executionId());
                                    if (!switched.get() || handoff.isEmpty()) {
                                        return Flux.<ExecutionEvent>empty();
                                    }
                                    return invoke(
                                            roleHandoffInvocation(
                                                    invocation,
                                                    handoff.orElseThrow(),
                                                    lastSequence.get()));
                                }))
                .doFinally(signal -> roleHandoffs.take(command.executionId()));
    }

    /**
     * 构造角色切换后的续接调用：同一 Run、新 executionId（由前一执行确定性派生），路由改为 {@code ROUTE_FIXED(toRoleKey,
     * skill=null)}，技能保持开放选择；续接后不再暴露 activate_role，每个 Run 最多切换一次。
     */
    private static AssistantInvocation roleHandoffInvocation(
            AssistantInvocation origin, DirectRoleHandoffRegistry.Handoff handoff, long sequence) {
        var command = origin.command();
        var intent = origin.executionIntent();
        var fixedIntent =
                new ExecutionIntent(
                        intent.interactionMode(),
                        ExecutionIntent.RouteConstraint.ROUTE_FIXED,
                        intent.clarificationPolicy(),
                        new ExecutionIntent.ResolvedRoute(
                                handoff.toRoleKey(), null, handoff.assistantRevision()),
                        intent.artifactPolicy(),
                        intent.actionAuthorizationPolicy(),
                        intent.workspaceId(),
                        intent.trustedProjectId(),
                        intent.decomposition(),
                        intent.aggregationRequirementsMaxChars());
        var profile = command.invocationProfile();
        var nextProfile =
                new InvocationProfile(
                        null,
                        profile.memoryMode(),
                        profile.invocationPolicy(),
                        profile.userAttachments(),
                        fixedIntent,
                        profile.contextPlan(),
                        profile.agentKind(),
                        profile.agentKey(),
                        profile.expectedAssistantRevision(),
                        profile.toolScopeRestricted(),
                        profile.allowedToolKeys());
        var nextExecutionId =
                new com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId(
                        UUID.nameUUIDFromBytes(
                                        ("role-handoff|" + command.executionId().value())
                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                                .toString());
        var nextCommand =
                command.forRoleHandoff(nextExecutionId, sequence, nextProfile, Instant.now());
        log.info(
                "[角色切换] 续接执行开始：runId={}，originExecutionId={}，executionId={}，roleKey={}",
                command.runId().value(),
                command.executionId().value(),
                nextExecutionId.value(),
                handoff.toRoleKey());
        return new AssistantInvocation(
                nextCommand,
                null,
                origin.memoryMode(),
                origin.invocationPolicy(),
                origin.userAttachments(),
                fixedIntent);
    }

    static boolean shouldPersistEvent(ExecutionEvent event) {
        return event.type() != ExecutionEventType.EXECUTION_PROMOTED
                && event.ownerType() == OwnerType.ASSISTANT
                && !isLiveOnlyContentDraftPreview(event);
    }

    private static boolean isLiveOnlyContentDraftPreview(ExecutionEvent event) {
        var values = event.payload().values();
        if (!"content.draft.upsert".equals(values.get("toolName"))
                || !"CONTENT_DRAFT".equals(values.get("previewKind"))) {
            return false;
        }
        return switch (event.type()) {
            case TOOL_CALL_ARGS_DELTA ->
                    isOnlyPreviewPayload(
                                    values,
                                    Set.of(
                                            "replyId",
                                            "toolCallId",
                                            "toolName",
                                            "previewKind",
                                            "contentDelta"))
                            && values.get("contentDelta") instanceof String;
            case TOOL_CALL_ARGS_COMPLETED ->
                    isOnlyPreviewPayload(
                                    values,
                                    Set.of(
                                            "replyId",
                                            "toolCallId",
                                            "toolName",
                                            "previewKind",
                                            "contentPreviewCompleted"))
                            && Boolean.TRUE.equals(values.get("contentPreviewCompleted"));
            default -> false;
        };
    }

    private static boolean isOnlyPreviewPayload(
            Map<String, Object> values, Set<String> allowedKeys) {
        return !values.isEmpty() && allowedKeys.containsAll(values.keySet());
    }

    private Flux<ExecutionEvent> executeNode(
            AssistantInvocation invocation, ExecutionTraceRecorder trace) {
        var command = invocation.command();
        var sequence = new AtomicLong(command.sequenceBase());
        var taskRef = new AtomicReference<>(requireTask(command));
        var definition = requireDefinition(command);
        definition.requireControlMode(command.controlMode());
        requirePublished(definition);
        logDefinitionResolved(command, definition);
        var profileCandidate = resolveExecutionProfile(invocation, definition);
        var context = executionContext(command, definition, profileCandidate);
        var profile = freezeCompressedProfile(invocation, profileCandidate, context.messages());
        traceProfile(trace, invocation, profile, context);
        log.debug(
                "[Assistant协调] L1 上下文已冻结：executionId={}，taskId={}，agentKind={}，来源数={}，消息数={}，摘要={}",
                command.executionId().value(),
                taskKey(command),
                command.invocationProfile().agentKind(),
                context.references().size(),
                context.messages().size(),
                context.digest());
        var agentEvents = new ArrayList<ExecutionEvent>();
        var executionEvents =
                agentExecution
                        .execute(
                                agentCommand(
                                        invocation,
                                        definition,
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
                executionEvents,
                Flux.defer(
                        () ->
                                finalizeNodeExecution(
                                        invocation,
                                        taskRef,
                                        profile,
                                        agentEvents,
                                        sequence,
                                        trace)));
    }

    private Flux<ExecutionEvent> executeDirect(
            AssistantInvocation invocation, ExecutionTraceRecorder trace) {
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
                .flatMapMany(ignored -> executeDirectAgent(invocation, trace));
    }

    private Flux<ExecutionEvent> executeDirectAgent(
            AssistantInvocation invocation, ExecutionTraceRecorder trace) {
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
        var roleSelectedBy = new AtomicReference<String>();
        var profileCandidate = resolveExecutionProfile(invocation, definition, roleSelectedBy);
        emitted.add(
                roleResolvedEvent(
                        command,
                        sequence.incrementAndGet(),
                        ExecutionEventStatus.RUNNING,
                        profileCandidate,
                        roleSelectedBy.get()));
        var controlledContext = executionContext(command, definition, profileCandidate);
        var profile =
                freezeCompressedProfile(invocation, profileCandidate, controlledContext.messages());
        traceProfile(trace, invocation, profile, controlledContext);
        var agentEvents = new ArrayList<ExecutionEvent>();
        var executionEvents =
                agentExecution
                        .execute(
                                agentCommand(
                                        invocation,
                                        definition,
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
                Flux.defer(
                        () ->
                                finalizeDirect(
                                        invocation, definition, agentEvents, sequence, trace)));
    }

    private Flux<ExecutionEvent> finalizeDirect(
            AssistantInvocation invocation,
            AssistantDefinition definition,
            List<ExecutionEvent> agentEvents,
            AtomicLong sequence,
            ExecutionTraceRecorder trace) {
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
                                        "",
                                        promotionDetails(command));
                            }
                            var handoff = roleHandoffs.peek(command.executionId());
                            if (handoff.isPresent()
                                    && agentEvents.stream()
                                            .noneMatch(
                                                    event ->
                                                            event.status()
                                                                    == ExecutionEventStatus
                                                                            .CANCELED)) {
                                // 角色切换收口：不跑完成校验、不写会话与长期记忆，正文由续接执行的新角色给出（ADR-011）
                                var switched = handoff.orElseThrow();
                                tasks.updateExecution(
                                        command.tenantId(),
                                        command.executionId(),
                                        Execution.Status.SUPERSEDED,
                                        Instant.now());
                                trace.validation("未验证（角色切换）", switched.reason());
                                log.info(
                                        "[角色切换] 前一执行已收口：executionId={}，status={}",
                                        command.executionId().value(),
                                        Execution.Status.SUPERSEDED);
                                return roleSwitchedEvent(
                                        command, sequence.incrementAndGet(), switched);
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
                            trace.validation(
                                    decision == null ? "未验证（已取消）" : decision.outcome().name(),
                                    decision == null ? null : decision.reason());
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
                                appendSessionTurn(command, reply, trace);
                            }
                            if (completed) {
                                learnLongTermMemory(
                                        memoryGovernance,
                                        command,
                                        reply,
                                        longTermMemoryEnabled(invocation, definition)
                                                && definition
                                                        .memoryStrategy()
                                                        .writeScopes()
                                                        .contains("PERSONAL"),
                                        trace);
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
                                        "",
                                        promotionDetails(command));
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
        return directTerminalEvent(
                command, sequence, type, status, reason, endReason, reply, Map.of());
    }

    /** 升级后协调者角色与形态，读取失败时只缺字段，不影响升级收口。 */
    private Map<String, Object> promotionDetails(AssistantCommand command) {
        var details = new LinkedHashMap<String, Object>();
        try {
            var taskId =
                    TaskCommandService.promotionTaskId(command.tenantId(), command.executionId());
            taskBoards
                    .find(command.tenantId(), taskId)
                    .map(plan -> plan.nodes().get("coordinator"))
                    .map(TaskPlan.TaskNode::roleKey)
                    .ifPresent(roleKey -> details.put("roleKey", roleKey));
            var checkpoint = tasks.findTask(command.tenantId(), taskId).map(Task::checkpoint);
            checkpoint
                    .map(value -> value.annotations().get("promotionShape"))
                    .filter(String.class::isInstance)
                    .ifPresent(shape -> details.put("shape", shape));
            // 升级触发方：网关携带待执行调用自动升级为 TOOL_GATE，模型显式调用 promote_to_task 为 MODEL
            var pendingCall =
                    checkpoint.flatMap(
                            value -> PendingToolCall.fromAnnotation(value.annotations()));
            details.put("trigger", pendingCall.isPresent() ? "TOOL_GATE" : "MODEL");
            pendingCall.ifPresent(call -> details.put("pendingToolName", call.toolName()));
        } catch (RuntimeException failure) {
            log.warn(
                    "[任务升级] 读取升级意图失败：executionId={}，错误类型={}",
                    command.executionId().value(),
                    failure.getClass().getName());
        }
        return details;
    }

    /** {@code promotionDetails} 仅 EXECUTION_PROMOTED 使用：升级后的协调者角色与形态（ADR-011）。 */
    private static ExecutionEvent directTerminalEvent(
            AssistantCommand command,
            long sequence,
            ExecutionEventType type,
            Execution.Status status,
            String reason,
            TurnEndReason endReason,
            String reply,
            Map<String, Object> promotionDetails) {
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
            values.putAll(promotionDetails);
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
        var aggregator = agentKind == InvocationProfile.AgentKind.AGGREGATOR;
        // 任务态内的协调者与聚合者沿用 Assistant 级长期记忆策略；轻量态协调者只遵从本次请求的记忆模式
        var taskDelegate =
                command.taskId() != null
                        && (aggregator || agentKind == InvocationProfile.AgentKind.COORDINATOR);
        // 只有聚合者仍是摘要披露；协调者与执行者可读取内容（ADR-008）
        var summaryOnly = aggregator;
        var scopes = EnumSet.noneOf(ContextScope.class);
        var memoryEnabled =
                profile.longTermMemoryEnabled()
                        || (taskDelegate
                                && definition.memoryStrategy().longTermEnabled()
                                && command.memorySubject().kind() == SubjectKind.USER);
        if (memoryEnabled) {
            scopes.add(ContextScope.MEMORY);
        }
        var contextPlan = command.invocationProfile().contextPlan();
        if (!contextPlan.taskMaterials().isEmpty()) {
            scopes.add(ContextScope.TASK_MATERIAL);
        }
        if (scopes.isEmpty()) {
            return com.xuejiai.aaf.framework.intelligent.cognition.model.ControlledContextSnapshot
                    .empty(command.requestedAt());
        }
        var purpose = aggregator ? AgentPurpose.AGGREGATION : AgentPurpose.EXECUTION;
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
                        command.conversationId().value(),
                        command.requestedAt()));
    }

    /**
     * 本次执行 knowledge_search 工具可检索的知识库范围：请求显式指定知识库时只取指定库；请求关闭知识库时为空； 否则取 助理默认库 ∪
     * 当前角色绑定库（助理库优先，总数受上限约束）。聚合者不检索知识库，恒为空。
     */
    static Set<UUID> effectiveKnowledgeBaseIds(
            AssistantCommand command, AssistantDefinition definition, String roleKey) {
        if (command.invocationProfile().agentKind() == InvocationProfile.AgentKind.AGGREGATOR) {
            return Set.of();
        }
        var plan = command.invocationProfile().contextPlan();
        if (plan.knowledgeDisabled()) {
            return Set.of();
        }
        if (plan.knowledgeQuery().enabled()) {
            return plan.knowledgeQuery().knowledgeBaseIds();
        }
        var knowledgeBaseIds = new LinkedHashSet<UUID>(definition.knowledgeBaseIds());
        definition.roles().stream()
                .filter(role -> role.key().equals(roleKey))
                .findFirst()
                .ifPresent(role -> knowledgeBaseIds.addAll(role.knowledgeBaseIds()));
        return knowledgeBaseIds.stream()
                .limit(MAX_DEFAULT_KNOWLEDGE_BASES)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 写入本轮短期会话上下文；失败只记录，不影响已完成的执行。 */
    private void appendSessionTurn(
            AssistantCommand command, String reply, ExecutionTraceRecorder trace) {
        var sessions = sessionMemories.getIfAvailable();
        if (sessions == null) {
            trace.sessionTurn("未启用");
            return;
        }
        try {
            sessions.appendTurn(
                    command.tenantId(),
                    command.userId(),
                    command.conversationId().value(),
                    command.input(),
                    reply);
            trace.sessionTurn("已写入");
        } catch (RuntimeException exception) {
            log.warn("[会话上下文] 短期会话写入失败，不影响本轮结果：{}", exception.getMessage());
            trace.sessionTurn("写入失败：" + exception.getClass().getSimpleName());
        }
    }

    /**
     * 长期记忆学习，尽力而为：回复已生成并判定完成，学习失败只记 WARN 与轨迹摘要，不改变本轮执行结果。
     *
     * <p>包级可见供单测直接验证失败不外抛。
     */
    static void learnLongTermMemory(
            MemoryGovernanceService governance,
            AssistantCommand command,
            String reply,
            boolean enabled,
            ExecutionTraceRecorder trace) {
        if (!enabled) {
            trace.longTermMemorySkipped();
            return;
        }
        try {
            var outcome =
                    governance.learn(
                            command.memorySubject(), command.input(), reply, Instant.now());
            trace.longTermMemoryLearned(outcome.stored(), outcome.rejected());
        } catch (RuntimeException exception) {
            log.warn(
                    "[长期记忆] 执行后学习失败，不影响本轮结果：executionId={}，taskId={}",
                    command.executionId().value(),
                    taskKey(command),
                    exception);
            trace.longTermMemoryFailed(exception);
        }
    }

    /** 把已冻结画像与 L1 上下文中的名称、数量写入轨迹摘要；不读取任何正文。 */
    private void traceProfile(
            ExecutionTraceRecorder trace,
            AssistantInvocation invocation,
            ExecutionProfileSnapshot profile,
            com.xuejiai.aaf.framework.intelligent.cognition.model.ControlledContextSnapshot
                    context) {
        if (!trace.enabled()) {
            return;
        }
        var command = invocation.command();
        var skillProfile = profile.skillExecutionProfile();
        trace.profile(
                command.invocationProfile().agentKind().name(),
                profile.roleAssignment().roleKey(),
                profile.roleAssignment().roleName(),
                skillProfile.activatedSkills().stream().map(skill -> skill.code()).toList(),
                invocation.requestedSkillKey(),
                skillProfile.effectiveTools().stream().map(ToolRef::name).toList(),
                profile.executionModel().map(ModelSpec::modelId).orElse(null));
        var references = context.references();
        trace.context(
                countSources(references, EffectiveContextManifest.SourceType.MEMORY),
                countSources(references, EffectiveContextManifest.SourceType.KNOWLEDGE),
                countSources(references, EffectiveContextManifest.SourceType.TASK_MATERIAL),
                profile.contextCompression()
                        .map(compression -> compression.finalEstimatedTokens())
                        .orElse(null),
                runtimeExecutionPolicy.contextWindow());
    }

    private static int countSources(
            List<EffectiveContextManifest.SourceReference> references,
            EffectiveContextManifest.SourceType type) {
        return (int) references.stream().filter(reference -> reference.type() == type).count();
    }

    /** 协调者节点本轮成功提交委派计划时，读取计划把子节点写入摘要。仅摘要开启时读取；读取失败只影响摘要， 不影响节点收口。 */
    private void traceDelegation(
            ExecutionTraceRecorder trace, AssistantCommand command, List<ExecutionEvent> events) {
        if (!trace.enabled()
                || command.nodeIdentity().kind() != NodeIdentity.NodeKind.COORDINATOR
                || events.stream()
                        .noneMatch(
                                event ->
                                        event.type() == ExecutionEventType.TOOL_CALL_COMPLETED
                                                && SubmitCoordinationPlanTool.TOOL_NAME.equals(
                                                        event.payload()
                                                                .values()
                                                                .get("toolName")))) {
            return;
        }
        try {
            taskBoards
                    .find(command.tenantId(), command.taskId())
                    .ifPresent(plan -> tracePlan(trace, plan, command.nodeIdentity().nodeId()));
        } catch (RuntimeException exception) {
            log.debug(
                    "[执行轨迹] 读取委派计划失败，摘要不列子节点：executionId={}，错误类型={}",
                    command.executionId().value(),
                    exception.getClass().getName());
        }
    }

    /** 协调者委派出的子节点（不含自身）写入轨迹摘要。 */
    private static void tracePlan(ExecutionTraceRecorder trace, TaskPlan plan, String selfNodeId) {
        trace.plan(
                plan.nodes().values().stream()
                        .filter(node -> !node.nodeId().equals(selfNodeId))
                        .sorted(Comparator.comparing(TaskPlan.TaskNode::nodeId))
                        .map(
                                node ->
                                        new ExecutionTraceRecorder.ChildNode(
                                                node.nodeId(),
                                                node.kind().name(),
                                                node.roleKey(),
                                                node.status() == null
                                                        ? null
                                                        : node.status().name()))
                        .toList());
    }

    private Flux<ExecutionEvent> executeAgent(
            AssistantInvocation invocation,
            AtomicReference<Task> taskRef,
            AtomicBoolean taskProgressed,
            ExecutionTraceRecorder trace) {
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
        var roleSelectedBy = new AtomicReference<String>();
        var profileCandidate = resolveExecutionProfile(invocation, definition, roleSelectedBy);
        emitted.add(
                roleResolvedEvent(
                        command,
                        sequence.incrementAndGet(),
                        task,
                        profileCandidate,
                        roleSelectedBy.get()));

        var controlledContext = executionContext(command, definition, profileCandidate);
        var profile =
                freezeCompressedProfile(invocation, profileCandidate, controlledContext.messages());
        traceProfile(trace, invocation, profile, controlledContext);
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
                        definition,
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
                        () ->
                                finalizeTask(
                                        invocation,
                                        taskRef,
                                        profile,
                                        agentEvents,
                                        sequence,
                                        trace)));
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

    /** tenantId 即 orgId 的字符串投影；非数字（如测试占位）时不绑定组织上下文。 */
    private static Long orgIdOf(AssistantCommand command) {
        try {
            return Long.valueOf(command.tenantId().value());
        } catch (NumberFormatException ignored) {
            return null;
        }
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
            AtomicLong sequence,
            ExecutionTraceRecorder trace) {
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
            return finalizeNodeExecution(
                    invocation, taskRef, profile, agentEvents, sequence, trace);
        }
        var plan = taskBoards.find(command.tenantId(), command.taskId());
        if (trace.enabled()) {
            plan.ifPresent(found -> tracePlan(trace, found, null));
        }
        var decision =
                completionValidator.validate(
                        new CompletionValidator.ValidationRequest(
                                taskRef.get(), command.completionCriteria(), plan, agentEvents));
        trace.validation(decision.outcome().name(), decision.reason());
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
                    appendSessionTurn(command, reply, trace);
                }
                learnLongTermMemory(
                        memoryGovernance,
                        command,
                        reply,
                        longTermMemoryEnabled(invocation, definition)
                                && definition.memoryStrategy().writeScopes().contains("PERSONAL"),
                        trace);
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
            AtomicLong sequence,
            ExecutionTraceRecorder trace) {
        var command = invocation.command();
        var task = taskRef.get();
        // 授权/澄清等待已由 HITL 事务原子提交（Task、Execution 等待态 + Dispatch 关闭 + canonical 事件），
        // 节点此时已失去租约，不再追加校验与暂停事件，也不提交结果
        var committedWait =
                agentEvents.stream()
                        .filter(event -> command.executionId().equals(event.executionId()))
                        .filter(AssistantApplicationService::committedHumanWait)
                        .findFirst()
                        .orElse(null);
        if (committedWait != null) {
            trace.validation("AWAITING_HUMAN", "等待已随 HITL 事务提交");
            traceDelegation(trace, command, agentEvents);
            log.info(
                    "[AssistantExecution] stage=task_node_validation executionId={} taskId={} nodeId={} "
                            + "outcome=AWAITING_HUMAN waitEventType={} agentEventCount={}",
                    command.executionId().value(),
                    taskKey(command),
                    command.nodeIdentity().nodeId(),
                    committedWait.type(),
                    agentEvents.size());
            return Flux.empty();
        }
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
        trace.validation(decision.outcome().name(), decision.reason());
        traceDelegation(trace, command, agentEvents);
        var draftEvidence =
                agentEvents.stream()
                        .filter(event -> event.type() == ExecutionEventType.TOOL_CALL_COMPLETED)
                        .filter(
                                event ->
                                        "DRAFT"
                                                .equals(
                                                        event.payload()
                                                                .values()
                                                                .get("artifactState")))
                        .findFirst()
                        .orElse(null);
        log.info(
                "[AssistantExecution] stage=task_node_validation executionId={} taskId={} nodeId={} "
                        + "outcome={} reason={} agentEventCount={} toolCallId={} documentId={} "
                        + "documentVersionId={}",
                command.executionId().value(),
                taskKey(command),
                command.nodeIdentity().nodeId(),
                decision.outcome(),
                decision.reason(),
                agentEvents.size(),
                draftEvidence == null ? null : draftEvidence.payload().values().get("toolCallId"),
                draftEvidence == null ? null : draftEvidence.payload().values().get("artifactId"),
                draftEvidence == null
                        ? null
                        : draftEvidence.payload().values().get("artifactVersionId"));
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
            case NEEDS_USER -> throw new IllegalStateException("NEEDS_USER 必须伴随已提交的等待事件");
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

    /** 随 HITL 事务落库的 canonical 等待事件（授权/澄清）。 */
    private static boolean committedHumanWait(ExecutionEvent event) {
        return switch (event.type()) {
            case AUTHORIZATION_REQUESTED ->
                    event.status() == ExecutionEventStatus.AWAITING_AUTHORIZATION;
            case CLARIFICATION_REQUESTED ->
                    event.status() == ExecutionEventStatus.AWAITING_CLARIFICATION;
            default -> false;
        };
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
        return resolveExecutionProfile(invocation, definition, new AtomicReference<>());
    }

    /**
     * {@code selectedByOut} 回传本次角色来源，供 ROLE_RESOLVED 事件公开；复用已冻结画像时为 {@code
     * EXECUTION_PROFILE_REUSED}。
     */
    private ExecutionProfileSnapshot resolveExecutionProfile(
            AssistantInvocation invocation,
            AssistantDefinition definition,
            AtomicReference<String> selectedByOut) {
        var command = invocation.command();
        var expectedRevision = command.invocationProfile().expectedAssistantRevision();
        if (expectedRevision != null && expectedRevision != definition.version().value()) {
            throw new IllegalStateException("Team 成员 Assistant revision 已失效");
        }
        var existing = executionProfiles.find(command.tenantId(), command.executionId());
        if (existing.isPresent()) {
            selectedByOut.set("EXECUTION_PROFILE_REUSED");
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
        var route = effectiveRoute(command, intent, definition);
        if (route != null && route.assistantRevision() != definition.version().value()) {
            throw new IllegalStateException("FIXED Route 的 Assistant revision 已失效");
        }
        // 内置任务类技能按智能体类型与容器状态过滤，与控制工具可见性保持一致（BuiltinSkillScope）
        var systemBindings =
                BuiltinSkillScope.filter(
                        SkillBinding.copyOf(
                                systemSkillBindings.findEnabled(), "systemSkillBindings"),
                        command.invocationProfile().agentKind(),
                        command.taskId() != null,
                        intent.decomposition());
        final Role selectedRole;
        final String roleSelectedBy;
        final String roleSelectionReason;
        if (route != null) {
            // 节点的 Role/Skill 已在计划阶段校验，请求 Skill 只约束根命令
            if (command.nodeIdentity() == null
                    && intent.routeConstraint()
                            == com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent
                                    .RouteConstraint.ROUTE_FIXED
                    && !Objects.equals(invocation.requestedSkillKey(), route.skillKey())) {
                throw new IllegalArgumentException("请求 Skill 与 FIXED Route 不一致");
            }
            selectedRole =
                    definition.roles().stream()
                            .filter(candidate -> candidate.key().equals(route.roleKey()))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "FIXED Route 的 Role 不属于 Assistant: "
                                                            + route.roleKey()));
            roleSelectedBy = "SERVER_FIXED_ROUTE";
            roleSelectionReason = "使用服务端已解析的不可变 Route";
        } else {
            // 未锁定角色（ADR-011）：从会话当前角色起步，失效或未记录时用助理默认角色；切换由协调者 activate_role 完成
            var conversationRoleKey = conversationRoleKey(command);
            var conversationRole =
                    conversationRoleKey == null
                            ? Optional.<Role>empty()
                            : definition.roles().stream()
                                    .filter(
                                            candidate ->
                                                    candidate.key().equals(conversationRoleKey))
                                    .findFirst();
            if (conversationRole.isPresent()) {
                selectedRole = conversationRole.orElseThrow();
                roleSelectedBy = "CONVERSATION_ROLE";
                roleSelectionReason = "未锁定角色，沿用会话当前角色";
            } else {
                if (conversationRoleKey != null) {
                    log.warn(
                            "[角色选择] 会话角色已失效，回退默认角色：executionId={}，conversationId={}，"
                                    + "会话角色={}，默认角色={}",
                            command.executionId().value(),
                            command.conversationId().value(),
                            conversationRoleKey,
                            definition.defaultRole().key());
                }
                selectedRole = definition.defaultRole();
                roleSelectedBy = "DEFAULT_ROLE";
                roleSelectionReason = "未锁定角色且无有效会话角色，使用助理默认角色";
            }
        }
        var role = definition.requireRole(selectedRole.key());
        selectedByOut.set(roleSelectedBy);
        log.info(
                "[角色选择] 角色已确定：executionId={}，taskId={}，roleKey={}，selectedBy={}，"
                        + "固定路由={}，意图路由约束={}",
                command.executionId().value(),
                taskKey(command),
                role.key(),
                roleSelectedBy,
                route != null,
                intent.routeConstraint());
        appendRoleAudit(command, role, roleSelectedBy, roleSelectionReason);
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
        var selectedOnDemandSkillKey =
                route != null && route.skillKey() != null
                        ? route.skillKey()
                        : invocation.requestedSkillKey();
        var selectionBindings =
                selectedOnDemandSkillKey == null
                        ? onDemandBindings
                        : onDemandBindings.stream()
                                .filter(
                                        binding ->
                                                binding.binding()
                                                        .skillKey()
                                                        .equals(selectedOnDemandSkillKey))
                                .toList();
        if (selectedOnDemandSkillKey != null && selectionBindings.size() != 1) {
            throw new IllegalArgumentException(
                    "请求 Skill 不在当前执行 Scope 候选范围: " + selectedOnDemandSkillKey);
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
                "SKILL_SCOPE_FROZEN",
                null,
                "AAF_POLICY",
                "仅冻结当前 Scope 的 ON_DEMAND Skill 摘要，正文按显式请求或 context.load 加载");
        var selectedOnDemandKeys =
                selectExplicitSkillKeys(
                        selection,
                        route == null ? null : route.skillKey(),
                        invocation.requestedSkillKey());
        var selectedBy =
                invocation.requestedSkillKey() != null
                        ? "USER_REQUEST"
                        : route != null && route.skillKey() != null
                                ? "SERVER_FIXED_ROUTE"
                                : "CONTEXT_LOAD";
        var selectionReason =
                invocation.requestedSkillKey() != null
                        ? "用户显式指定 Skill，直接加载冻结版本正文"
                        : route != null && route.skillKey() != null
                                ? "使用服务端固定 ON_DEMAND Skill"
                                : "未显式指定 Skill，由协调者通过 context.load 按需加载";
        var finalBindings =
                scopedBindings.stream()
                        .filter(
                                binding ->
                                        binding.binding().activationMode()
                                                        == SkillActivationMode.ALWAYS
                                                || selectedOnDemandKeys.contains(
                                                        binding.binding().skillKey()))
                        .toList();
        var alwaysCandidates =
                finalBindings.stream()
                        .filter(
                                binding ->
                                        binding.binding().activationMode()
                                                == SkillActivationMode.ALWAYS)
                        .map(this::authorizedSkillSummary)
                        .toList();
        var selectedOnDemandCandidates =
                selectionCandidates.stream()
                        .filter(candidate -> selectedOnDemandKeys.contains(candidate.code()))
                        .toList();
        var activatedCandidates =
                new ArrayList<
                        com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizedSkillSummary>();
        activatedCandidates.addAll(alwaysCandidates);
        activatedCandidates.addAll(selectedOnDemandCandidates);
        var resolvedSkills = effectiveSkillResolver.resolve(activatedCandidates);
        var activatedSkills = activateSkills(finalBindings, resolvedSkills);
        verifyActivatedSkillVersions(activatedCandidates, activatedSkills);
        log.debug(
                "[AssistantExecution] stage=role_resolved executionId={} taskId={} roleKey={} "
                        + "selectedBy={} onDemandCandidateCount={} requestedSkill={}",
                command.executionId().value(),
                taskKey(command),
                role.key(),
                selectedBy,
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
                                        : selectedBy,
                                skill.activationMode() == SkillActivationMode.ALWAYS
                                        ? "绑定声明 ALWAYS，自动激活但仍执行工具授权"
                                        : selectionReason));
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
        var roleAllowed = allowedToolNames(role.toolKeys(), command, definition, "Role");
        var roleAllowedToolNames = roleAllowed.allowed();
        var assistantAllowed =
                allowedToolNames(definition.assistantToolKeys(), command, definition, "Assistant");
        var assistantAllowedToolNames = assistantAllowed.allowed();
        // 聚合者是唯一无业务工具的智能体；协调者与执行者同样持有 Role/Skill 解析出的业务工具（ADR-008）
        var aggregator =
                command.invocationProfile().agentKind() == InvocationProfile.AgentKind.AGGREGATOR;
        var declaredSkillRequirements =
                java.util.stream.Stream.concat(
                                roleSkillRequirements.stream(), assistantSkillRequirements.stream())
                        .collect(Collectors.toUnmodifiableSet());
        var allowedToolNames =
                java.util.stream.Stream.concat(
                                roleAllowedToolNames.stream(), assistantAllowedToolNames.stream())
                        .collect(Collectors.toUnmodifiableSet());
        // 保存意图由交付角色确定性推导：轻量态直答、计划唯一完成证据所在的协调者、聚合者是交付节点，
        // 只有它们要求并持有产物保存工具；执行者等中间节点既不要求也不可见，避免产出零散草稿（ADR-008）
        var deliverer = command.nodeIdentity() == null || command.nodeIdentity().userFacing();
        var saveToolName = intent.autoSaveDraft() ? intent.artifactPolicy().saveTool() : null;
        if (saveToolName != null && deliverer) {
            if (!declaredSkillRequirements.contains(saveToolName)
                    || !allowedToolNames.contains(saveToolName)) {
                throw new IllegalStateException("产物保存工具不在 Skill 固定执行画像内: " + saveToolName);
            }
        }
        var agentAllowedTools = toolRefs(allowedToolNames);
        var roleAgentAllowedTools =
                roleAllowedToolNames.isEmpty() ? List.<ToolRef>of() : agentAllowedTools;
        // 已配置但仅因当前 controlMode 被过滤的 Skill 必需工具需升级任务才可用：从本次画像收窄并提示模型，
        // 不再让只读请求整体失败；不在授权内或被调用 scope 过滤的工具仍交给解析器 fail-closed。
        var taskRequiredToolNames = new java.util.TreeSet<String>();
        taskRequiredToolNames.addAll(roleAllowed.requiringTask(roleSkillRequirements));
        taskRequiredToolNames.addAll(assistantAllowed.requiringTask(assistantSkillRequirements));
        if (!taskRequiredToolNames.isEmpty()) {
            log.info(
                    "[AssistantExecution] stage=skill_tools_require_task executionId={} taskId={} "
                            + "controlMode={} skillCodes={} toolNames={}",
                    command.executionId().value(),
                    taskKey(command),
                    command.controlMode(),
                    skillCodes(activatedSkills),
                    taskRequiredToolNames);
            appendSelectionAudit(
                    command,
                    role,
                    selection,
                    "SKILL_TOOLS_REQUIRE_TASK",
                    null,
                    "AAF_POLICY",
                    "controlMode=%s 下以下 Skill 必需工具仅任务态可用，已从本次画像收窄：%s"
                            .formatted(command.controlMode(), taskRequiredToolNames));
        }
        // 工具声明的 required 语义：选中的工具都进入画像；required=false 的工具缺失授权时跳过，
        // 只在 Skill 工具清单中标为不可用。任一已激活 Skill 声明为 required 即按必需处理。
        var toolDeclarations =
                skillTools.findByVersionIds(
                        activatedSkills.stream()
                                .map(skill -> skill.version().versionId())
                                .collect(Collectors.toUnmodifiableSet()));
        var requiredDeclaredToolNames =
                toolDeclarations.values().stream()
                        .flatMap(List::stream)
                        .filter(SkillToolCatalogPort.SkillToolDeclaration::required)
                        .map(SkillToolCatalogPort.SkillToolDeclaration::toolName)
                        .collect(Collectors.toUnmodifiableSet());
        var optionalToolNames =
                toolDeclarations.values().stream()
                        .flatMap(List::stream)
                        .map(SkillToolCatalogPort.SkillToolDeclaration::toolName)
                        .filter(toolName -> !requiredDeclaredToolNames.contains(toolName))
                        .collect(Collectors.toUnmodifiableSet());
        final List<ToolRef> roleEffectiveTools;
        try {
            roleEffectiveTools =
                    effectiveToolResolver.resolve(
                            withoutUnavailableOptional(
                                    roleAllowed.narrow(roleSkillRequirements),
                                    optionalToolNames,
                                    roleAllowedToolNames),
                            roleInheritRoleTools,
                            roleAllowedToolNames,
                            roleAgentAllowedTools);
        } catch (IllegalStateException failure) {
            throw skillToolResolutionFailure(failure, activatedRoleSkills, command);
        }
        final List<ToolRef> assistantEffectiveTools;
        try {
            assistantEffectiveTools =
                    activatedAssistantSkills.isEmpty()
                            ? List.<ToolRef>of()
                            : effectiveToolResolver.resolveAssistant(
                                    withoutUnavailableOptional(
                                            assistantAllowed.narrow(assistantSkillRequirements),
                                            optionalToolNames,
                                            assistantAllowedToolNames),
                                    assistantInheritRoleTools,
                                    assistantAllowedToolNames,
                                    agentAllowedTools);
        } catch (IllegalStateException failure) {
            throw skillToolResolutionFailure(failure, activatedAssistantSkills, command);
        }
        var resolvedTools = mergeEffectiveTools(roleEffectiveTools, assistantEffectiveTools);
        // 最后一维交集：目录声明的 permissionCode 与执行用户实际持有的功能权限；只收窄可见性，不改调用期授权。
        var permittedTools = toolVisibility.visible(resolvedTools, command.userId().value());
        // 委派强制（REQUIRED）时协调者只做规划，业务工具一并不可见；否则协调者可绕过委派自己完成
        var delegationRequired =
                command.taskId() != null
                        && command.invocationProfile().agentKind()
                                == InvocationProfile.AgentKind.COORDINATOR
                        && intent.decomposition()
                                == ExecutionIntent.DecompositionStrategy.DECOMPOSE_REQUIRED;
        final List<ToolRef> businessTools;
        if (delegationRequired) {
            businessTools = List.of();
        } else if (aggregator) {
            // 聚合者无业务工具，唯一例外是产物保存工具：最终聚合结果由它保存，仍走 ToolGateway 授权
            businessTools =
                    saveToolName == null
                            ? List.of()
                            : permittedTools.stream()
                                    .filter(tool -> tool.name().equals(saveToolName))
                                    .toList();
        } else if (saveToolName != null && !deliverer) {
            businessTools =
                    permittedTools.stream()
                            .filter(tool -> !tool.name().equals(saveToolName))
                            .toList();
        } else {
            businessTools = permittedTools;
        }
        var baseEffectiveTools =
                mergeEffectiveTools(
                        mergeEffectiveTools(
                                businessTools, BuiltinControlToolProfile.resolve(command)),
                        BuiltinControlToolProfile.resolveKnowledgeSearch(
                                effectiveKnowledgeBaseIds(command, definition, role.key())));
        // 轻量态可升级时，仅因 controlMode 被过滤的 Skill 必需工具对模型可见但只能触发升级（promotionOnly）：
        // 网关在 grant 查询与副作用之前拦截并自动转为任务；解析器的 fail-closed 不受影响，仍需通过用户功能权限
        var promotionOnlyToolNames =
                command.taskId() == null
                                && baseEffectiveTools.stream()
                                        .anyMatch(
                                                tool ->
                                                        tool.name()
                                                                .equals(
                                                                        PromoteDirectTaskTool
                                                                                .TOOL_NAME))
                        ? toolVisibility
                                .visible(toolRefs(taskRequiredToolNames), command.userId().value())
                                .stream()
                                .map(ToolRef::name)
                                .collect(Collectors.toUnmodifiableSet())
                        : Set.<String>of();
        var effectiveTools =
                promotionOnlyToolNames.isEmpty()
                        ? baseEffectiveTools
                        : mergeEffectiveTools(
                                baseEffectiveTools,
                                toolRefs(new java.util.TreeSet<>(promotionOnlyToolNames)));
        if (!promotionOnlyToolNames.isEmpty()) {
            log.info(
                    "[AssistantExecution] stage=promotion_only_tools executionId={} toolNames={}",
                    command.executionId().value(),
                    new java.util.TreeSet<>(promotionOnlyToolNames));
        }
        log.info(
                "[AssistantExecution] stage=skill_resolved executionId={} taskId={} selectionMode={} "
                        + "skillCodes={} skillVersions={} selectedBy={} effectiveToolCount={} "
                        + "effectiveToolNames={} saveTool={} saveToolVisible={} deliveryNode={}",
                command.executionId().value(),
                taskKey(command),
                selection.selectionMode(),
                activatedSkills.stream().map(skill -> skill.code()).toList(),
                activatedSkills.stream().map(skill -> skill.version().toString()).toList(),
                selectedBy,
                effectiveTools.size(),
                effectiveTools.stream().map(ToolRef::name).sorted().toList(),
                saveToolName,
                saveToolName != null
                        && effectiveTools.stream()
                                .anyMatch(tool -> tool.name().equals(saveToolName)),
                deliverer);
        var authorizationRules = new LinkedHashMap<String, ToolAuthorizationRule>();
        effectiveTools.forEach(
                tool -> {
                    if (BuiltinControlToolProfile.contains(tool.name())) {
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
                    if (promotionOnlyToolNames.contains(tool.name())) {
                        // 只能触发升级：不经 controlMode 校验，网关对其恒按需授权处理并自动转任务
                        var policyRule = definition.toolPolicy().rules().get(tool.name());
                        authorizationRules.put(
                                tool.name(),
                                new ToolAuthorizationRule(
                                        switch (policyRule.effect()) {
                                            case READ, GENERATED_CONTENT, HUMAN_HANDOFF -> true;
                                            case REVERSIBLE_WRITE, IRREVERSIBLE_WRITE -> false;
                                        },
                                        policyRule.reversible(),
                                        true,
                                        ToolAuthorizationContext.MissingGrantBehavior
                                                .REQUEST_ON_DEMAND,
                                        true));
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
                command.taskId() != null || !role.key().equals(definition.defaultRoleKey())
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
                                command.executionId(),
                                new DomainContextPort.Scope(
                                        command.tenantId(),
                                        command.userId(),
                                        intent.workspaceId(),
                                        intent.trustedProjectId()),
                                invocation.requestedSkillKey(),
                                command.taskId() != null,
                                skillToolLines(
                                        activatedSkills,
                                        toolDeclarations,
                                        effectiveTools,
                                        taskRequiredToolNames,
                                        command.taskId() == null)));
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
                                            .ContextDisclosurePolicy.coordinator();
                            case AGGREGATOR ->
                                    com.xuejiai.aaf.framework.intelligent.assistant.model
                                            .ContextDisclosurePolicy.aggregatorSummary();
                            case EXECUTOR ->
                                    com.xuejiai.aaf.framework.intelligent.assistant.model
                                            .ContextDisclosurePolicy.executorMinimal();
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
            AssistantDefinition definition,
            ExecutionProfileSnapshot profile,
            long sequenceBase,
            List<AgentMessage> finalMessages) {
        var command = invocation.command();
        var execution =
                command.taskId() == null
                        ? null
                        : tasks.findExecution(command.tenantId(), command.executionId())
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "DELEGATED Agent 调用缺少 canonical Execution"));
        var stateSlotId =
                execution == null ? command.executionId().value() : execution.stateSlotId();
        var context =
                new InvocationContext(
                        command.tenantId(),
                        command.userId(),
                        profile.executionIntent().workspaceId(),
                        profile.executionIntent().trustedProjectId(),
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
                        command.dispatchLeaseUntil(),
                        profile.executionIntent().artifactPolicy().target(),
                        Optional.of(profile.skillExecutionProfile()),
                        effectiveKnowledgeBaseIds(
                                command, definition, profile.roleAssignment().roleKey()));
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
                command.resumeStateRequired(),
                seededToolCall(command, profile, execution));
    }

    /**
     * 协调者节点首轮重放轻量态携带的待执行调用（AAF-128）：仅协调者节点、非恢复、attempt 1 且 Task checkpoint 有携带调用时返回；工具不在本次有效工具内时
     * fail-closed，节点按现有失败策略重试，attempt 2 走普通模型路径。
     */
    private PendingToolCall seededToolCall(
            AssistantCommand command, ExecutionProfileSnapshot profile, Execution execution) {
        if (execution == null
                || command.nodeIdentity() == null
                || command.nodeIdentity().kind() != NodeIdentity.NodeKind.COORDINATOR
                || command.resumeStateRequired()
                || execution.attemptNo() != 1) {
            return null;
        }
        var checkpoint =
                tasks.findTask(command.tenantId(), command.taskId())
                        .map(Task::checkpoint)
                        .orElse(null);
        if (checkpoint == null) {
            return null;
        }
        var call = PendingToolCall.fromAnnotation(checkpoint.annotations()).orElse(null);
        if (call == null) {
            return null;
        }
        if (profile.skillExecutionProfile().effectiveTools().stream()
                .noneMatch(tool -> tool.name().equals(call.toolName()))) {
            throw new IllegalStateException("待执行调用的工具不在协调者节点有效工具内: " + call.toolName());
        }
        return call;
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
                summary.version(),
                List.of(),
                summary.requiredModelCapabilities(),
                summary.requiredToolNames());
    }

    private static Set<String> selectExplicitSkillKeys(
            com.xuejiai.aaf.framework.intelligent.agent.model.SkillSelectionManifest selection,
            String fixedSkillKey,
            String requestedSkillKey) {
        var selectedKey = requestedSkillKey == null ? fixedSkillKey : requestedSkillKey;
        if (selectedKey == null) {
            return Set.of();
        }
        var candidate =
                selection.candidates().stream()
                        .filter(summary -> summary.code().equals(selectedKey))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "请求 Skill 不在当前执行 Scope 候选范围: " + selectedKey));
        if (fixedSkillKey != null && !fixedSkillKey.equals(candidate.code())) {
            throw new IllegalArgumentException("请求 Skill 与 FIXED Route 不一致");
        }
        return Set.of(candidate.code());
    }

    private static void verifyActivatedSkillVersions(
            List<com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizedSkillSummary>
                    candidates,
            List<com.xuejiai.aaf.framework.intelligent.agent.model.ActivatedSkill>
                    activatedSkills) {
        var versionsByCode =
                candidates.stream()
                        .collect(
                                Collectors.toMap(
                                        candidate -> candidate.code(),
                                        candidate -> candidate.version(),
                                        (left, right) -> {
                                            throw new IllegalStateException("Skill 候选摘要包含重复 code");
                                        }));
        activatedSkills.stream()
                .forEach(
                        skill -> {
                            var expected = versionsByCode.get(skill.code());
                            if (expected == null || !expected.equals(skill.version())) {
                                throw new IllegalStateException(
                                        "SkillVersion 在执行画像冻结前发生漂移: " + skill.code());
                            }
                        });
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
            AssistantCommand command, Role role, String selectedBy, String reason) {
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
                        null,
                        "ROLE_SELECTED",
                        null,
                        null,
                        role.skillKeys().stream().sorted().toList(),
                        null,
                        reason,
                        selectedBy,
                        List.of(),
                        null,
                        null,
                        command.correlationId().value(),
                        Instant.now()));
    }

    /** 会话当前角色；未提供端口实现或读取失败时视为无会话角色，不影响本轮执行。 */
    private String conversationRoleKey(AssistantCommand command) {
        var port = conversationRoles.getIfAvailable();
        if (port == null) {
            return null;
        }
        try {
            return port.find(command.tenantId(), command.conversationId())
                    .filter(value -> !value.isBlank())
                    .map(String::trim)
                    .orElse(null);
        } catch (RuntimeException failure) {
            log.warn(
                    "[会话角色] 读取失败，按无会话角色处理：conversationId={}，错误类型={}",
                    command.conversationId().value(),
                    failure.getClass().getName(),
                    failure);
            return null;
        }
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
                profile.skillExecutionProfile().onDemandCandidates().stream()
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

    /**
     * 解析本次执行实际生效的路由。任务态节点（有 Task 与节点身份）一律使用建板时冻结在节点上的 Role 与 Skill： 协调者委派的执行者可以使用不同于根路由的
     * Role/Skill（经计划校验），且任务态不再做 L0 选择（ADR-008）。 非节点执行使用意图里已冻结的路由；轻量态 AUTO 返回 {@code null} 走选择器。
     */
    private static ExecutionIntent.ResolvedRoute effectiveRoute(
            AssistantCommand command, ExecutionIntent intent, AssistantDefinition definition) {
        var node = command.nodeIdentity();
        if (command.taskId() != null && node != null && node.roleKey() != null) {
            return new ExecutionIntent.ResolvedRoute(
                    node.roleKey(), node.skillKey(), definition.version().value());
        }
        return intent.resolvedRoute();
    }

    /**
     * 按调用 scope 与当前 controlMode 过滤 Role/Assistant 已配置工具。
     *
     * <p>返回值同时给出"已配置且在 scope 内、仅因 controlMode 被过滤"的工具，供画像编译区分"需升级任务"与"未授权"。
     */
    private static AllowedToolNames allowedToolNames(
            Set<String> configuredToolNames,
            AssistantCommand command,
            AssistantDefinition definition,
            String owner) {
        var allowed = new java.util.TreeSet<String>();
        var controlModeFiltered = new java.util.TreeSet<String>();
        configuredToolNames.stream()
                .filter(
                        toolName ->
                                !command.invocationProfile().toolScopeRestricted()
                                        || command.invocationProfile()
                                                .allowedToolKeys()
                                                .contains(toolName))
                .forEach(
                        toolName -> {
                            var rule =
                                    Objects.requireNonNull(
                                            definition.toolPolicy().rules().get(toolName),
                                            owner + " 工具缺少 Assistant ToolPolicy: " + toolName);
                            var permitted =
                                    definition
                                                    .toolPolicy()
                                                    .allows(command.controlMode(), rule.effect())
                                            && (command.controlMode()
                                                            != ExecutionEvent.ControlMode
                                                                    .COLLABORATIVE
                                                    || rule.effect()
                                                            != com.xuejiai.aaf.framework.intelligent
                                                                    .assistant.model.ToolPolicy
                                                                    .ActionEffect.REVERSIBLE_WRITE
                                                    || rule.reversible());
                            (permitted ? allowed : controlModeFiltered).add(toolName);
                        });
        return new AllowedToolNames(allowed, controlModeFiltered);
    }

    /**
     * Role/Assistant 工具在当前调用下的过滤结果。
     *
     * @param allowed 当前 controlMode 下可用的工具
     * @param controlModeFiltered 已配置且在调用 scope 内、仅因当前 controlMode 被过滤的工具
     */
    private record AllowedToolNames(Set<String> allowed, Set<String> controlModeFiltered) {

        AllowedToolNames {
            allowed = Set.copyOf(allowed);
            controlModeFiltered =
                    java.util.Collections.unmodifiableSortedSet(
                            new java.util.TreeSet<>(controlModeFiltered));
        }

        /** Skill 必需工具中需升级任务才可用的部分（按名称排序）；不在授权内的工具不计入，仍由解析器 fail-closed。 */
        java.util.SortedSet<String> requiringTask(Set<String> skillRequirements) {
            return java.util.Collections.unmodifiableSortedSet(
                    skillRequirements.stream()
                            .filter(controlModeFiltered::contains)
                            .collect(Collectors.toCollection(java.util.TreeSet::new)));
        }

        /** 从 Skill 必需工具中移除需升级任务的工具，剩余部分交给解析器校验。 */
        Set<String> narrow(Set<String> skillRequirements) {
            return skillRequirements.stream()
                    .filter(toolName -> !controlModeFiltered.contains(toolName))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    private static List<String> skillCodes(
            List<com.xuejiai.aaf.framework.intelligent.agent.model.ActivatedSkill> skills) {
        return skills.stream().map(skill -> skill.code()).sorted().toList();
    }

    /** 从交给解析器的必需工具中移除 required=false 且当前未授权的工具，使其缺失时跳过而非 fail-closed。 */
    private static Set<String> withoutUnavailableOptional(
            Set<String> skillRequirements, Set<String> optionalToolNames, Set<String> allowed) {
        return skillRequirements.stream()
                .filter(
                        toolName ->
                                !optionalToolNames.contains(toolName) || allowed.contains(toolName))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 按 Skill 声明顺序生成每个已激活 Skill 的工具清单状态：对话态可升级且仅因 controlMode 收窄的可见工具为需要审批（直接调用即自动转任务）；
     * 其余在本次画像中即可用；未授权、可选缺失、委派强制、任务态被收窄等为本次不可直接调用。
     */
    private static Map<String, List<PromptAssembler.SkillToolLine>> skillToolLines(
            List<com.xuejiai.aaf.framework.intelligent.agent.model.ActivatedSkill> skills,
            Map<Long, List<SkillToolCatalogPort.SkillToolDeclaration>> declarations,
            List<ToolRef> effectiveTools,
            Set<String> taskRequiredToolNames,
            boolean direct) {
        var visible = effectiveTools.stream().map(ToolRef::name).collect(Collectors.toSet());
        var promotable = direct && visible.contains(PromoteDirectTaskTool.TOOL_NAME);
        var lines = new LinkedHashMap<String, List<PromptAssembler.SkillToolLine>>();
        for (var skill : skills) {
            var declared = declarations.getOrDefault(skill.version().versionId(), List.of());
            if (declared.isEmpty()) {
                continue;
            }
            lines.put(
                    skill.code(),
                    declared.stream()
                            .map(
                                    declaration ->
                                            new PromptAssembler.SkillToolLine(
                                                    declaration.toolName(),
                                                    promotable
                                                                    && visible.contains(
                                                                            declaration.toolName())
                                                                    && taskRequiredToolNames
                                                                            .contains(
                                                                                    declaration
                                                                                            .toolName())
                                                            ? PromptAssembler.ToolAvailability
                                                                    .REQUIRES_TASK
                                                            : visible.contains(
                                                                            declaration.toolName())
                                                                    ? PromptAssembler
                                                                            .ToolAvailability
                                                                            .AVAILABLE
                                                                    : PromptAssembler
                                                                            .ToolAvailability
                                                                            .UNAVAILABLE))
                            .toList());
        }
        return lines;
    }

    /** 解析器拿不到 Skill 与 controlMode，由调用方补全后原样 fail-closed。 */
    private static IllegalStateException skillToolResolutionFailure(
            IllegalStateException failure,
            List<com.xuejiai.aaf.framework.intelligent.agent.model.ActivatedSkill> skills,
            AssistantCommand command) {
        return new IllegalStateException(
                "%s；skillCodes=%s，controlMode=%s"
                        .formatted(failure.getMessage(), skillCodes(skills), command.controlMode()),
                failure);
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
            AssistantCommand command,
            long sequence,
            Task task,
            ExecutionProfileSnapshot profile,
            String selectedBy) {
        return roleResolvedEvent(command, sequence, status(task.status()), profile, selectedBy);
    }

    private static ExecutionEvent roleResolvedEvent(
            AssistantCommand command,
            long sequence,
            ExecutionEventStatus eventStatus,
            ExecutionProfileSnapshot profile,
            String selectedBy) {
        var payload =
                new ExecutionEventPayload(
                        java.util.Map.of(
                                "roleKey", profile.roleAssignment().roleKey(),
                                "roleName", profile.roleAssignment().roleName(),
                                "routeConstraint",
                                        profile.executionIntent().routeConstraint().name(),
                                "interactionMode",
                                        profile.executionIntent().interactionMode().name(),
                                "selectedBy",
                                        Objects.requireNonNullElse(
                                                selectedBy, "EXECUTION_PROFILE_REUSED")));
        return event(
                command, sequence, ExecutionEventType.ROLE_RESOLVED, eventStatus, null, payload);
    }

    /** 轻量态角色切换事实；理由截断并去掉换行后进入公开 payload，与公共事件展示文本约束一致。 */
    private static ExecutionEvent roleSwitchedEvent(
            AssistantCommand command, long sequence, DirectRoleHandoffRegistry.Handoff handoff) {
        var values = new LinkedHashMap<String, Object>();
        values.put("scope", Execution.Scope.DIRECT.name());
        values.put("executionStatus", Execution.Status.SUPERSEDED.name());
        if (handoff.fromRoleKey() != null) {
            values.put("fromRoleKey", handoff.fromRoleKey());
        }
        values.put("toRoleKey", handoff.toRoleKey());
        values.put("toRoleName", singleLine(handoff.toRoleName()));
        values.put("reason", singleLine(handoff.reason()));
        return event(
                command,
                sequence,
                ExecutionEventType.ROLE_SWITCHED,
                ExecutionEventStatus.COMPLETED,
                null,
                new ExecutionEventPayload(values));
    }

    private static String singleLine(String text) {
        var normalized = text == null ? "" : text.replaceAll("[\\r\\n]+", " ").trim();
        return normalized.length() <= 256 ? normalized : normalized.substring(0, 256);
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
