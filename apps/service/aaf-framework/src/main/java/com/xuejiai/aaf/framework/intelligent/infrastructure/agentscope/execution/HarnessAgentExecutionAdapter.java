package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.execution;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand;
import com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage;
import com.xuejiai.aaf.framework.intelligent.agent.model.ExecutionPolicy;
import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentExecutionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextBudgetExceededException;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;
import com.xuejiai.aaf.framework.intelligent.core.prompt.InvocationMode;
import com.xuejiai.aaf.framework.intelligent.core.prompt.InvocationPurpose;
import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptInputKind;
import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptLengthSummary;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.compiler.AgentScopeSpecCompiler;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeEventMapper;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeEventMapper.MappingState;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeFailureClassifier.DuplicateExecutionSubscriptionException;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeFailureClassifier.ExecutionDeadlineExceededException;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeMessageMapper;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeRuntimeContextMapper;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.middleware.AgentScopeTokenMeteringObserver;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool.ToolResultEvidenceStore;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;

/**
 * AgentExecutionPort 的唯一 AgentScope Harness 实现。
 *
 * <p>职责：解析执行规格 → 编译 core ReActAgent → 订阅 {@code streamEvents} 事件流 → 映射为 AAF
 * 事件并顺序入库。同时维护活跃执行表以支持取消与中断。
 */
@Slf4j
public final class HarnessAgentExecutionAdapter implements AgentExecutionPort {

    private final AgentDefinitionPort definitions;
    private final AgentScopeSpecCompiler compiler;
    private final AgentScopeMessageMapper messageMapper;
    private final AgentScopeRuntimeContextMapper contextMapper;
    private final AgentStateStore stateStore;
    private final AgentScopeEventMapper eventMapper;
    private final AgentScopeTokenMeteringObserver meteringObserver;
    private final ExecutionEventStorePort eventStore;
    private final ConversationLeasePort leases;
    private final DelegatedTaskPort delegatedTasks;
    private final ToolResultEvidenceStore evidenceStore;

    /**
     * 承载源事件校验与计量的阻塞调度器（RQ-02）。
     *
     * <p>租约校验走 Redis、任务校验与计量走 JPA，都是同步阻塞 I/O。它们绝不能在 AgentScope 事件发射线程上执行——那会让 Redis/DB
     * 抖动直接转化为模型流背压与取消延迟，并且共享 SDK 线程被占用时会放大到其他执行。ADR-003 的请求虚拟线程不覆盖 Reactor 回调线程，必须显式切换。
     */
    private final Scheduler blockingScheduler;

    /** 活跃执行表：executionId → 运行态句柄，cancel 依赖它定位 Agent 与 RuntimeContext。 */
    private final ConcurrentMap<ExecutionId, ActiveExecution> activeExecutions =
            new ConcurrentHashMap<>();

    /**
     * 需要在进入模型/工具前做 fail-closed 校验的边界事件（RQ-02）。
     *
     * <p>逐个 token 增量都查一次 Redis + DB
     * 属于纯粹浪费：真正需要"确认租约仍是当代、任务仍允许执行"的时刻是产生外部副作用之前——启动、每次模型调用、每次工具调用与其结果落地。文本增量不触发副作用，跳过校验不放宽任何安全边界。
     */
    private static final Set<AgentEventType> GUARDED_EVENT_TYPES =
            EnumSet.of(
                    AgentEventType.AGENT_START,
                    AgentEventType.AGENT_END,
                    AgentEventType.MODEL_CALL_START,
                    AgentEventType.MODEL_CALL_END,
                    AgentEventType.TOOL_CALL_START,
                    AgentEventType.TOOL_RESULT_END,
                    AgentEventType.REQUIRE_USER_CONFIRM,
                    AgentEventType.REQUEST_STOP);

    public HarnessAgentExecutionAdapter(
            AgentDefinitionPort definitions,
            AgentScopeSpecCompiler compiler,
            AgentScopeMessageMapper messageMapper,
            AgentScopeRuntimeContextMapper contextMapper,
            AgentStateStore stateStore,
            AgentScopeEventMapper eventMapper,
            AgentScopeTokenMeteringObserver meteringObserver,
            ExecutionEventStorePort eventStore,
            ConversationLeasePort leases,
            DelegatedTaskPort delegatedTasks,
            ToolResultEvidenceStore evidenceStore,
            Scheduler blockingScheduler) {
        this.definitions = Objects.requireNonNull(definitions, "definitions 不能为空");
        this.compiler = Objects.requireNonNull(compiler, "compiler 不能为空");
        this.messageMapper = Objects.requireNonNull(messageMapper, "messageMapper 不能为空");
        this.contextMapper = Objects.requireNonNull(contextMapper, "contextMapper 不能为空");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore 不能为空");
        this.eventMapper = Objects.requireNonNull(eventMapper, "eventMapper 不能为空");
        this.meteringObserver = Objects.requireNonNull(meteringObserver, "meteringObserver 不能为空");
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore 不能为空");
        this.leases = Objects.requireNonNull(leases, "leases 不能为空");
        this.delegatedTasks = Objects.requireNonNull(delegatedTasks, "delegatedTasks 不能为空");
        this.evidenceStore = Objects.requireNonNull(evidenceStore, "evidenceStore 不能为空");
        this.blockingScheduler =
                Objects.requireNonNull(blockingScheduler, "blockingScheduler 不能为空");
    }

    /** 订阅时才真正解析与编译，保证每次 subscribe 都是独立执行。 */
    @Override
    public Flux<ExecutionEvent> execute(AgentExecutionCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        return Flux.defer(() -> replaySettledExecution(command))
                .switchIfEmpty(Flux.defer(() -> executeDeferred(command)));
    }

    /**
     * 重放守卫（RQ-05）：同一 {@code executionId} 已经产出过终态事件时，不得再次调用模型，只回放已持久事件。
     *
     * <p>{@code activeExecutions} 只能拒绝同进程内的并发重复订阅，覆盖不到"客户端重试 / 崩溃恢复 / 重复调度"这三种跨进程重投——{@code
     * JpaTaskRecoveryAdapter} 的 {@code asResume} 保留原 {@code executionId} 与 {@code
     * sequenceBase}，因此崩溃恢复必然重投同一执行。以持久事件里的终态 作为幂等判据：终态已存在即该执行已结算，回放 AGENT 事件即可，模型与工具副作用不再发生。
     *
     * <p>只查 {@code sequenceBase} 之后的事件：基线之前的事件属于 Assistant 在本次执行前写入的部分
     * （ROLE_RESOLVED、任务状态变更等），不是本适配器的产物。
     */
    private Flux<ExecutionEvent> replaySettledExecution(AgentExecutionCommand command) {
        var context = command.context();
        return eventStore
                .readExecution(context.tenantId(), context.executionId(), command.sequenceBase())
                .filter(event -> event.ownerType() == OwnerType.AGENT)
                .collectList()
                .flatMapMany(
                        stored -> {
                            if (stored.stream().noneMatch(HarnessAgentExecutionAdapter::terminal)) {
                                if (!stored.isEmpty()) {
                                    log.warn(
                                            "[AgentLoop] 检测到未终结的历史 AGENT 事件，按恢复继续执行：executionId={}，历史事件数={}",
                                            context.executionId().value(),
                                            stored.size());
                                }
                                return Flux.empty();
                            }
                            log.info(
                                    "[AgentLoop] 命中重放守卫，跳过模型调用并回放已持久事件：executionId={}，事件数={}",
                                    context.executionId().value(),
                                    stored.size());
                            return Flux.fromIterable(stored);
                        });
    }

    private static boolean terminal(ExecutionEvent event) {
        return event.type() == ExecutionEventType.RUN_COMPLETED
                || event.type() == ExecutionEventType.RUN_FAILED
                || event.type() == ExecutionEventType.EXECUTION_CANCELED;
    }

    /**
     * 取消：只在首次调用且执行未终结时生效，Agent 已启动才能下发 interrupt。
     *
     * <p>返回值与终态仲裁同源——{@code compareAndSet} 到 {@code CANCELLING} 成功才返回 {@code true}，因此不会出现"cancel 返回
     * true 但从未发出取消事件"或"取消与正常完成 都各自发了一次终态"的竞态（RQ-01）。
     */
    @Override
    public Mono<Boolean> cancel(ExecutionId executionId) {
        Objects.requireNonNull(executionId, "executionId 不能为空");
        return Mono.fromSupplier(
                () -> {
                    var active = activeExecutions.get(executionId);
                    if (active == null) {
                        log.debug(
                                "[AgentLoop] 忽略中断请求：executionId={}，原因=未找到活跃执行",
                                executionId.value());
                        return false;
                    }
                    if (!active.terminal()
                            .compareAndSet(TerminalState.ACTIVE, TerminalState.CANCELLING)) {
                        log.debug(
                                "[AgentLoop] 忽略中断请求：executionId={}，原因=已进入终态或已标记取消，当前={}",
                                executionId.value(),
                                active.terminal().get());
                        return false;
                    }
                    log.debug(
                            "[AgentLoop] 已接收中断请求：executionId={}，Agent已启动={}，将按状态下发中断",
                            executionId.value(),
                            active.started().get());
                    interruptIfCancelledAndStarted(active);
                    return true;
                });
    }

    /** 解析阶段的异常也要落成 RUN_FAILED 事件，避免调用方拿到空流。 */
    private Flux<ExecutionEvent> executeDeferred(AgentExecutionCommand command) {
        requireCurrent(command);
        var mappingState = new MappingState(command.sequenceBase());
        try {
            command.compiledSystemPrompt().verify();
            command.compiledSystemPrompt().requireCompatible(command.subagentSpec());
            var execution = resolveExecution(command);
            return executeResolved(command, mappingState, execution);
        } catch (RuntimeException failure) {
            return eventStore
                    .append(
                            eventMapper.failure(
                                    command,
                                    command.subagentSpec().identifier(),
                                    mappingState,
                                    failure),
                            command.context().lease())
                    .flux();
        }
    }

    /** 这里只能观察 Harness invocation 初始输入，不包含后续 ReAct Tool Result、最终 Tool Schema 或 provider 包装。 */
    private static void logPromptPreflight(
            AgentExecutionCommand command, ResolvedExecution execution) {
        var contents = new EnumMap<PromptInputKind, List<String>>(PromptInputKind.class);
        for (var kind : PromptInputKind.values()) {
            contents.put(kind, new ArrayList<>());
        }
        contents.get(PromptInputKind.SYSTEM).add(command.compiledSystemPrompt().content());
        for (var message : command.messages()) {
            contents.get(promptInputKind(command, message)).add(message.text());
        }
        command.skillExecutionProfile().effectiveTools().stream()
                .map(tool -> "%s@%d:%s".formatted(tool.toolId(), tool.version(), tool.name()))
                .forEach(contents.get(PromptInputKind.TOOL_REFERENCE)::add);
        var attachmentCount =
                command.messages().stream().mapToInt(message -> message.attachments().size()).sum();
        var lengths = PromptLengthSummary.measure(contents, attachmentCount);
        var contextWindow = execution.executionPolicy().contextWindow();
        var system = lengths.input(PromptInputKind.SYSTEM);
        var currentUser = lengths.input(PromptInputKind.CURRENT_USER_INPUT);
        var otherUser = lengths.input(PromptInputKind.OTHER_USER_INPUT);
        var assistantHistory = lengths.input(PromptInputKind.ASSISTANT_HISTORY);
        var assistantReasoning = lengths.input(PromptInputKind.ASSISTANT_REASONING);
        var controlledContext = lengths.input(PromptInputKind.CONTROLLED_CONTEXT);
        var toolResult = lengths.input(PromptInputKind.TOOL_RESULT);
        var toolReference = lengths.input(PromptInputKind.TOOL_REFERENCE);
        log.info(
                "[Prompt预检] boundary=HARNESS_INVOCATION mode={} logicalInvocationId={} purpose={} model={} systemChars={} systemEstimatedTokens={} currentUserChars={} currentUserEstimatedTokens={} otherUserChars={} otherUserEstimatedTokens={} assistantHistoryChars={} assistantHistoryEstimatedTokens={} assistantReasoningChars={} assistantReasoningEstimatedTokens={} controlledContextChars={} controlledContextEstimatedTokens={} toolResultChars={} toolResultEstimatedTokens={} toolReferenceChars={} toolReferenceEstimatedTokens={} attachmentCount={} totalChars={} totalEstimatedTokens={} contextWindow={} contextUsagePercent={}",
                InvocationMode.AUTONOMOUS_HARNESS,
                command.context().runId().value(),
                InvocationPurpose.HARNESS_EXECUTION,
                execution.model().modelId(),
                system.characters(),
                system.estimatedTokensAtFourCodePoints(),
                currentUser.characters(),
                currentUser.estimatedTokensAtFourCodePoints(),
                otherUser.characters(),
                otherUser.estimatedTokensAtFourCodePoints(),
                assistantHistory.characters(),
                assistantHistory.estimatedTokensAtFourCodePoints(),
                assistantReasoning.characters(),
                assistantReasoning.estimatedTokensAtFourCodePoints(),
                controlledContext.characters(),
                controlledContext.estimatedTokensAtFourCodePoints(),
                toolResult.characters(),
                toolResult.estimatedTokensAtFourCodePoints(),
                toolReference.characters(),
                toolReference.estimatedTokensAtFourCodePoints(),
                lengths.attachmentCount(),
                lengths.totalCharacters(),
                lengths.totalEstimatedTokens(),
                contextWindow,
                lengths.usagePercentOf(contextWindow));
    }

    private static PromptInputKind promptInputKind(
            AgentExecutionCommand command, AgentMessage message) {
        return switch (message.role()) {
            case SYSTEM ->
                    throw new IllegalArgumentException(
                            "Harness messages 禁止追加 SYSTEM；SYSTEM 只能来自 CompiledSystemPrompt");
            case ASSISTANT -> PromptInputKind.ASSISTANT_HISTORY;
            case REASONING -> PromptInputKind.ASSISTANT_REASONING;
            case TOOL -> PromptInputKind.TOOL_RESULT;
            case USER -> {
                var currentMessageId = "user:" + command.context().runId().value();
                if (currentMessageId.equals(message.messageId())) {
                    yield PromptInputKind.CURRENT_USER_INPUT;
                }
                yield controlledContextMessage(message.messageId())
                        ? PromptInputKind.CONTROLLED_CONTEXT
                        : PromptInputKind.OTHER_USER_INPUT;
            }
        };
    }

    private static boolean controlledContextMessage(String messageId) {
        return "controlled-context-summary".equals(messageId)
                || messageId.startsWith("l1-knowledge:")
                || messageId.startsWith("memory-context:")
                || messageId.startsWith("aaf-context-summary:")
                || messageId.startsWith("task-material:");
    }

    private Flux<ExecutionEvent> executeResolved(
            AgentExecutionCommand command, MappingState mappingState, ResolvedExecution execution) {
        var executionId = command.context().executionId();
        final RuntimeContext runtimeContext;
        final String stateUserKey;
        try {
            logPromptPreflight(command, execution);
            log.debug(
                    "[AgentLoop] AgentScope 执行体已就绪：executionId={}，AAF规格类型={}，agent={}，执行模式={}，现场编译临时实例={}，模型={}，工具数={}，消息数={}，promptSha256={}，promptLength={}",
                    command.context().executionId().value(),
                    command.subagentSpec().getClass().getSimpleName(),
                    execution.agentIdentifier(),
                    command.executionMode(),
                    execution.ephemeral(),
                    execution.model().modelId(),
                    command.skillExecutionProfile().effectiveTools().size(),
                    command.messages().size(),
                    command.compiledSystemPrompt().sha256(),
                    command.compiledSystemPrompt()
                            .content()
                            .codePointCount(0, command.compiledSystemPrompt().content().length()));
            stateUserKey =
                    contextMapper.stateUserKey(command.context(), execution.agentIdentifier());
            runtimeContext =
                    contextMapper.toAgentScope(command.context(), execution.agentIdentifier());
            requireNoHiddenPersistentHistory(command, stateUserKey);
        } catch (RuntimeException failure) {
            release(execution);
            throw failure;
        }

        var active = new ActiveExecution(execution.agent(), runtimeContext, execution.ephemeral());
        try {
            // executionId 唯一：同一执行不允许并发订阅两次
            var existing = activeExecutions.putIfAbsent(executionId, active);
            if (existing != null) {
                release(executionId, active);
                // RQ-11：抛可识别的稳定异常而非裸 IllegalStateException，且不写持久终态——
                // 失败方与胜出方共享 executionId，写入 RUN_FAILED 会让重放守卫误判该执行已结算
                return Flux.error(
                        new DuplicateExecutionSubscriptionException(
                                "executionId 已存在活跃执行: " + executionId.value()));
            }
            log.debug(
                    "[AgentLoop] 已注册活跃执行并订阅 Harness 事件流：executionId={}，agent={}，临时实例={}",
                    executionId.value(),
                    execution.agentIdentifier(),
                    execution.ephemeral());

            var policy = execution.executionPolicy();
            // 总时限的完成信号：主流终止时置空，避免"等待一个永不完成的计时器"把正常完成拖到时限到点
            var mainTerminated = Sinks.<ExecutionEvent>one();
            return execution
                    .agent()
                    .streamEvents(messageMapper.toAgentScope(command.messages()), runtimeContext)
                    // 每个源事件：边界事件校验租约与任务 → 记录启动态 → 计量 token。
                    // 阻塞 I/O 一律切到 blockingScheduler，不占用 AgentScope 事件发射线程（RQ-02）；
                    // concatMap 保持逐事件串行，fail-closed 语义与原先完全一致
                    .concatMap(
                            event ->
                                    Mono.fromCallable(
                                                    () -> {
                                                        if (GUARDED_EVENT_TYPES.contains(
                                                                event.getType())) {
                                                            requireCurrent(command);
                                                        }
                                                        onSourceEvent(event, active);
                                                        meteringObserver.observe(
                                                                event, execution.model(), command);
                                                        return event;
                                                    })
                                            .subscribeOn(blockingScheduler))
                    // 收敛为 AAF 事件，无对应语义的源事件被丢弃
                    .concatMap(
                            event ->
                                    Mono.justOrEmpty(
                                            eventMapper.map(
                                                    event,
                                                    command,
                                                    execution.agentIdentifier(),
                                                    execution.model(),
                                                    mappingState)))
                    // 事件静默超时：只约束相邻已映射事件之间的间隔，识别"连接未断但模型卡住"
                    .timeout(policy.idleTimeout())
                    .doOnComplete(mainTerminated::tryEmitEmpty)
                    .doOnError(ignored -> mainTerminated.tryEmitEmpty())
                    // 总墙钟硬时限：计时从订阅开始，与事件是否持续产出无关（RQ-03）。
                    // 持续输出事件不再能无限延长执行，两个时限各自独立表达一种边界
                    .mergeWith(totalDeadline(mainTerminated, policy.totalTimeout(), executionId))
                    .doOnError(ignored -> interruptOnce(active))
                    // 异常收口：终态是单一 CAS 仲裁的结果，不再由分散的布尔标志推断。
                    // 先抢 ACTIVE→TERMINATED：抢到即"这是一次未被取消覆盖的真实失败"，发 RUN_FAILED。
                    // 抢不到说明 cancel() 已先行 CAS 到 CANCELLING，退而抢 CANCELLING→TERMINATED
                    // 发 EXECUTION_CANCELED——取消请求的调用方看到的必须是取消终态，不能是失败终态。
                    .onErrorResume(
                            failure ->
                                    Flux.just(
                                            active.terminal()
                                                            .compareAndSet(
                                                                    TerminalState.ACTIVE,
                                                                    TerminalState.TERMINATED)
                                                    ? eventMapper.failure(
                                                            command,
                                                            execution.agentIdentifier(),
                                                            mappingState,
                                                            failure)
                                                    : cancelWonRace(active)
                                                            ? eventMapper.canceled(
                                                                    command,
                                                                    execution.agentIdentifier(),
                                                                    mappingState)
                                                            : eventMapper.failure(
                                                                    command,
                                                                    execution.agentIdentifier(),
                                                                    mappingState,
                                                                    failure)))
                    // 正常完成路径：同一套仲裁。抢到 ACTIVE→TERMINATED 即静默结束；
                    // 抢不到说明 cancel() 已胜出，补发一次 EXECUTION_CANCELED——不会与上面的
                    // onErrorResume 分支重复执行，两者是同一订阅里互斥的终态路径。
                    .concatWith(
                            Flux.defer(
                                    () ->
                                            active.terminal()
                                                            .compareAndSet(
                                                                    TerminalState.ACTIVE,
                                                                    TerminalState.TERMINATED)
                                                    ? Flux.empty()
                                                    : cancelWonRace(active)
                                                            ? Flux.just(
                                                                    eventMapper.canceled(
                                                                            command,
                                                                            execution
                                                                                    .agentIdentifier(),
                                                                            mappingState))
                                                            : Flux.empty()))
                    // concatMap 保证串行入库，sequence 由存储层原子分配；写入 SLA 与模型侧时限解耦
                    .concatMap(
                            event ->
                                    eventStore
                                            .append(event, command.context().lease())
                                            .timeout(policy.persistTimeout()))
                    .doFinally(ignored -> release(executionId, active, command, stateUserKey));
        } catch (RuntimeException failure) {
            release(executionId, active, command, stateUserKey);
            throw failure;
        }
    }

    /**
     * 总时限信号：与事件流并行的独立计时器，到点即以 {@link ExecutionDeadlineExceededException} 终止整条流。
     *
     * <p>计时器等待的是"主流已终止"信号——主流正常结束时该信号立即置空，计时器随之完成，合并流不会被计时器拖住； 主流迟迟不结束时 {@code timeout}
     * 触发，超时同样走终态仲裁与 {@code onErrorResume}，落成一条带 {@code failureCategory=TIMEOUT} 的 RUN_FAILED
     * 事件，而不是把裸异常抛给调用方。
     */
    private static Flux<ExecutionEvent> totalDeadline(
            Sinks.One<ExecutionEvent> mainTerminated,
            Duration totalTimeout,
            ExecutionId executionId) {
        return mainTerminated
                .asMono()
                .timeout(totalTimeout)
                .onErrorMap(
                        TimeoutException.class,
                        ignored ->
                                new ExecutionDeadlineExceededException(
                                        "执行总时限耗尽: executionId="
                                                + executionId.value()
                                                + "，totalTimeout="
                                                + totalTimeout))
                .flux();
    }

    /** AgentScope 会先恢复 agent_state 再追加 command messages；隐藏历史未纳入冻结画像时必须拒绝。 */
    private void requireNoHiddenPersistentHistory(
            AgentExecutionCommand command, String stateUserKey) {
        var context = command.context();
        final java.util.Optional<AgentState> state;
        try {
            state =
                    stateStore.get(
                            stateUserKey,
                            context.sessionId().value(),
                            "agent_state",
                            AgentState.class);
        } catch (RuntimeException failure) {
            throw new ContextBudgetExceededException("无法验证 AgentScope 持久历史，拒绝进入模型", failure);
        }
        if (state.isEmpty()) {
            return;
        }
        var persisted = state.orElseThrow();
        if (!persisted.getContext().isEmpty() || !persisted.getSummary().isBlank()) {
            throw new ContextBudgetExceededException("检测到未纳入冻结画像的 AgentScope 持久历史，拒绝进入模型");
        }
    }

    /**
     * 解析执行规格：预定义 Agent 走版本化定义 + 共享缓存；动态规格按执行模式取用。
     *
     * <p>DIRECT（主助理直答）复用缓存实例，其余动态子智能体一次性编译，用完即 close（ephemeral）。
     */
    private ResolvedExecution resolveExecution(AgentExecutionCommand command) {
        return switch (command.subagentSpec()) {
            case SubagentSpec.Predefined predefined -> {
                var spec =
                        definitions
                                .findByIdAndVersion(predefined.agentId(), predefined.version())
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        "Agent 定义不存在: "
                                                                + predefined.agentId().value()
                                                                + "@"
                                                                + predefined.version()));
                yield new ResolvedExecution(
                        compiler.compile(
                                spec,
                                command.compiledSystemPrompt(),
                                command.skillExecutionProfile().effectiveTools()),
                        spec.model(),
                        predefined.identifier(),
                        spec.executionPolicy(),
                        false);
            }
            case SubagentSpec.Dynamic dynamic -> {
                var executionModel =
                        command.executionModel()
                                .orElseThrow(() -> new IllegalArgumentException("Dynamic 执行缺少模型"));
                var direct = command.executionMode() == AgentExecutionCommand.ExecutionMode.DIRECT;
                var agent =
                        direct
                                ? compiler.compileDirect(
                                        dynamic,
                                        executionModel,
                                        command.compiledSystemPrompt(),
                                        command.skillExecutionProfile().effectiveTools())
                                : compiler.compileDynamic(
                                        dynamic,
                                        executionModel,
                                        command.compiledSystemPrompt(),
                                        command.skillExecutionProfile().effectiveTools());
                yield new ResolvedExecution(
                        agent,
                        executionModel,
                        dynamic.identifier(),
                        dynamic.executionPolicy(),
                        !direct);
            }
        };
    }

    /** 委托态才需要校验：会话租约仍是当代 + 任务允许继续执行；直答态无此约束。 */
    private void requireCurrent(AgentExecutionCommand command) {
        var context = command.context();
        if (context.controlMode()
                != com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode
                        .DELEGATED) {
            return;
        }
        leases.requireCurrent(context.lease());
        delegatedTasks.requireAgentExecution(context);
    }

    /** AGENT_START 之后 Agent 才可被中断，因此在此补发早于启动到达的取消请求。 */
    private void onSourceEvent(AgentEvent event, ActiveExecution active) {
        if (event.getType() != AgentEventType.AGENT_START) {
            return;
        }
        active.started().set(true);
        interruptIfCancelledAndStarted(active);
    }

    private void interruptIfCancelledAndStarted(ActiveExecution active) {
        if (active.terminal().get() == TerminalState.CANCELLING && active.started().get()) {
            interruptOnce(active);
        }
    }

    /**
     * 在自然终态路径的 {@code ACTIVE→TERMINATED} 抢占失败后调用：把 {@code CANCELLING→TERMINATED}
     * 补到底，确认取消确实是本次终态的胜出方。
     *
     * <p>只在 {@link #onErrorResume} 与 {@link #executeResolved} 的 {@code concatWith} 分支被调用，
     * 且两者在同一订阅内互斥执行，因此这里的 CAS 预期总是成功；失败只可能是已被其中一方处理过 （不会发生，双重防御）。
     */
    private static boolean cancelWonRace(ActiveExecution active) {
        return active.terminal().compareAndSet(TerminalState.CANCELLING, TerminalState.TERMINATED);
    }

    /**
     * interrupt 幂等且不越过释放边界（RQ-11 幂等 / RQ-12 时序）。
     *
     * <p>两道门：{@code released} 判定本次执行是否已进入释放流程——core {@code ReActAgent.close()} 会清空 {@code
     * stateCache}，而 {@code interrupt(ctx)} 依赖 {@code getAgentState(uid, sid).interruptControl()}
     * 定位在飞调用，释放后再下发会作用在新建的 AgentState 上，信号静默丢失；{@code interruptIssued} 的 CAS 保证同一执行 只向 core 下发一次中断。
     *
     * <p>残留窗口：检查与下发之间仍可能有 {@code release} 插入。该窗口无害——{@code release} 只在订阅终止后的 {@code doFinally}
     * 执行，此时推理循环已在收尾，一次落空的中断不改变终态；且缓存实例不 close，根本不受影响。 彻底消除需要把中断与释放放进同一把锁，代价高于收益，故记录而不做。
     */
    private void interruptOnce(ActiveExecution active) {
        if (active.released().get()) {
            log.debug("[AgentLoop] 执行已释放，不再下发中断（core close 已清空状态缓存，下发会落空）");
            return;
        }
        if (active.interruptIssued().compareAndSet(false, true)) {
            active.agent().interrupt(active.runtimeContext());
            log.debug("[AgentLoop] 已向 core ReAct 执行体下发中断");
        }
    }

    /**
     * 出表并释放本次执行占用的全部资源。
     *
     * <p>无论正常完成、失败、取消还是订阅 dispose 都会走到这里，因此把三类清理放在同一处：
     *
     * <ul>
     *   <li>一次性 Agent 实例 close（缓存实例由 compiler 统一管理）
     *   <li>工具证据残留清零——TOOL_RESULT_END 未到达的暂存项在这里兜底删除（RQ-10）
     *   <li>本次执行私有的 AgentScope 状态槽删除，避免按 executionId 分槽后 Redis 无界增长（RQ-08/09 的配套清理）
     * </ul>
     *
     * <p><b>close 不是空操作</b>：core {@code ReActAgent.close()} 会 {@code unbindStateSaver} 并清空本地 {@code
     * stateCache}。而 {@code interrupt(ctx)} 是通过 {@code getAgentState(uid, sid).interruptControl()}
     * 定位在飞调用的，因此"先 close 再 interrupt"会拿到一个新建的 AgentState，中断信号静默丢失。当前 close 只发生在 订阅终止后的 {@code
     * doFinally}，此时循环已在收尾，故后果有限；该时序缺口登记为 RQ-12，由 #10305 用统一 lifecycle 门控关闭。
     *
     * <p>状态槽清理失败不影响执行结论：槽键含 executionId，残留项不会被其他执行读到。
     */
    private void release(
            ExecutionId executionId,
            ActiveExecution active,
            AgentExecutionCommand command,
            String stateUserKey) {
        release(executionId, active);
        evidenceStore.clear(executionId);
        deleteExecutionState(command, stateUserKey);
    }

    /** 状态槽清理走阻塞调度器：doFinally 可能运行在 AgentScope 事件线程上，Redis 删除不能占用它。 */
    private void deleteExecutionState(AgentExecutionCommand command, String stateUserKey) {
        blockingScheduler.schedule(
                () -> {
                    try {
                        stateStore.delete(stateUserKey, command.context().sessionId().value());
                    } catch (RuntimeException failure) {
                        log.warn(
                                "[AgentLoop] 执行状态槽清理失败，等待 Redis 侧过期：executionId={}，错误={}",
                                command.context().executionId().value(),
                                failure.getMessage());
                    }
                });
    }

    /**
     * 出表并释放一次性实例；缓存实例由 compiler 统一管理，不在此关闭。
     *
     * <p>{@code released} 对所有执行置位（不只 ephemeral），因为它同时是 {@link #interruptOnce} 的释放边界门（RQ-12）：
     * 一旦进入释放流程就不再下发中断。close 仍只对一次性实例执行。
     */
    private void release(ExecutionId executionId, ActiveExecution active) {
        activeExecutions.remove(executionId, active);
        if (active.released().compareAndSet(false, true) && active.ephemeral()) {
            active.agent().close();
        }
    }

    /** 尚未进入活跃表就失败时的释放路径。 */
    private static void release(ResolvedExecution execution) {
        if (execution.ephemeral()) {
            execution.agent().close();
        }
    }

    /** 解析结果：可执行 Agent + 计量与超时所需元数据；ephemeral 表示用完即销毁。 */
    private record ResolvedExecution(
            ReActAgent agent,
            ModelSpec model,
            String agentIdentifier,
            ExecutionPolicy executionPolicy,
            boolean ephemeral) {}

    /**
     * 单次执行的终态仲裁状态机（RQ-01 修复）。
     *
     * <p>替代此前分散的 {@code cancelled}/{@code cancellationEmitted}/{@code sourceCompleted}
     * 三个独立布尔量——它们各自原子但组合不原子，存在"取消与正常完成都各发一次终态"或"cancel() 返回 true 但从未发出取消事件"两个确定窗口。收敛为单一 {@link
     * AtomicReference} 上的 CAS 转移后，任一时刻只有一条路径能把状态推进到 {@link #TERMINATED}，终态事件恰好发一次。
     *
     * <ul>
     *   <li>{@link #ACTIVE} → 初始态，执行进行中，未被请求取消
     *   <li>{@link #CANCELLING} → {@code cancel()} 已 CAS 抢占，已发 interrupt，尚未确认终态
     *   <li>{@link #TERMINATED} → 终态已确定并即将/已发出，之后任何 CAS 都会失败
     * </ul>
     */
    private enum TerminalState {
        ACTIVE,
        CANCELLING,
        TERMINATED
    }

    /** 单次执行的运行态标志位，用于取消、中断与终态事件去重。 */
    private record ActiveExecution(
            ReActAgent agent,
            RuntimeContext runtimeContext,
            boolean ephemeral,
            AtomicBoolean released,
            AtomicBoolean started,
            AtomicBoolean interruptIssued,
            AtomicReference<TerminalState> terminal) {

        private ActiveExecution(
                ReActAgent agent, RuntimeContext runtimeContext, boolean ephemeral) {
            this(
                    agent,
                    runtimeContext,
                    ephemeral,
                    new AtomicBoolean(),
                    new AtomicBoolean(),
                    new AtomicBoolean(),
                    new AtomicReference<>(TerminalState.ACTIVE));
        }
    }
}
