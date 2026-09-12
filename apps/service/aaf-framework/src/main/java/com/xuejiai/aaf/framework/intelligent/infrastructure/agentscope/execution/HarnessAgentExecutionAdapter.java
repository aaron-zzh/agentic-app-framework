package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskUnitOfWork;
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
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.state.DispatchGuardedAgentStateStore;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.state.DispatchGuardedAgentStateStore.Registration;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool.ToolResultEvidenceStore;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.shutdown.GracefulShutdownManager;
import io.agentscope.core.state.AgentState;
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
    private final DispatchGuardedAgentStateStore stateStore;
    private final AgentScopeEventMapper eventMapper;
    private final AgentScopeTokenMeteringObserver meteringObserver;
    private final ExecutionEventStorePort eventStore;
    private final ConversationLeasePort leases;
    private final TaskUnitOfWork delegatedTasks;
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
            DispatchGuardedAgentStateStore stateStore,
            AgentScopeEventMapper eventMapper,
            AgentScopeTokenMeteringObserver meteringObserver,
            ExecutionEventStorePort eventStore,
            ConversationLeasePort leases,
            TaskUnitOfWork delegatedTasks,
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

    /**
     * 暂停当前活跃回合（AAF-110）：与 {@link #cancel} 的差异只在终态仲裁——{@code CANCELLING} 走常规取消收尾（删状态槎）， {@code
     * PAUSING} 让 {@code doFinally} 跳过删除，责任主体不变时下次同一 {@code executionId} 重新发起可续接对话历史。 中断下发逻辑与 {@code
     * cancel} 完全一致，只是终态标记不同。
     */
    @Override
    public Mono<Boolean> pause(ExecutionId executionId) {
        Objects.requireNonNull(executionId, "executionId 不能为空");
        return Mono.fromSupplier(
                () -> {
                    var active = activeExecutions.get(executionId);
                    if (active == null) {
                        log.debug(
                                "[AgentLoop] 忽略暂停请求：executionId={}，原因=未找到活跃执行",
                                executionId.value());
                        return false;
                    }
                    if (!active.terminal()
                            .compareAndSet(TerminalState.ACTIVE, TerminalState.PAUSING)) {
                        log.debug(
                                "[AgentLoop] 忽略暂停请求：executionId={}，原因=已进入终态或已标记取消，当前={}",
                                executionId.value(),
                                active.terminal().get());
                        return false;
                    }
                    log.debug(
                            "[AgentLoop] 已接收暂停请求：executionId={}，Agent已启动={}，将按状态下发中断",
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
                InvocationMode.AUTONOMOUS_AGENT_LOOP,
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
            active.stateRegistration()
                    .set(
                            stateStore.register(
                                    stateUserKey,
                                    command.context().sessionId().value(),
                                    command.context()));
            requireNoHiddenPersistentHistory(command, stateUserKey);
            active.stateAccessReady().set(true);
            log.debug(
                    "[AgentLoop] 已注册活跃执行并订阅 Harness 事件流：executionId={}，agent={}，临时实例={}",
                    executionId.value(),
                    execution.agentIdentifier(),
                    execution.ephemeral());

            // 接入 core 优雅停机生命周期（AAF-110 #11004）：JVM 优雅停机时状态由 core 原生标记
            // shutdownInterrupted 并持久化，而不是被本适配器的 doFinally 无差别删除。绑定幂等
            // （GracefulShutdownManager 内部按 agentId 覆盖式写入），缓存实例跨多次执行复用时重复
            // 绑定同一 saver 无副作用。
            GracefulShutdownManager.getInstance()
                    .bindStateSaver(
                            execution.agent(),
                            state ->
                                    stateStore.save(
                                            state.getUserId(),
                                            state.getSessionId(),
                                            "agent_state",
                                            state));
            active.shutdownRequestId()
                    .set(GracefulShutdownManager.getInstance().registerRequest(execution.agent()));

            var policy = execution.executionPolicy();
            // 总时限的完成信号：主流终止时置空，避免"等待一个永不完成的计时器"把正常完成拖到时限到点
            var mainTerminated = Sinks.<ExecutionEvent>one();
            return execution
                    .agent()
                    .streamEvents(messageMapper.toAgentScope(command.messages()), runtimeContext)
                    // 每个源事件：边界事件校验租约与任务 → 记录启动态 → 计量 token。
                    // 已原子提交的 canonical HITL 挂起先读取并核验 exact event，再保存 AgentState；
                    // 只有这一条事件可绕过已关闭 Dispatch 的 current 校验并直接回送当前 run。
                    .concatMap(
                            event ->
                                    prepareSourceEvent(
                                            event, active, command, execution, stateUserKey))
                    // 收敛为 AAF 事件；canonical HITL 挂起直接使用事务已落库事件，不重新生成第二个语义事件
                    .concatMap(
                            receipt -> {
                                if (receipt.committedEvent() != null) {
                                    active.alreadyPersistedEventIds()
                                            .add(receipt.committedEvent().eventId().value());
                                    return Mono.just(receipt.committedEvent());
                                }
                                return Mono.justOrEmpty(
                                        eventMapper.map(
                                                receipt.sourceEvent(),
                                                command,
                                                execution.agentIdentifier(),
                                                execution.model(),
                                                mappingState));
                            })
                    .doOnNext(event -> preserveHitlState(event, active, command, stateUserKey))
                    // canonical waiting event 已完成控制权交接；旧 Dispatch 的尾随 Agent 事件不得继续进入映射/持久化
                    .takeUntil(HarnessAgentExecutionAdapter::canonicalWait)
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
                    // 抢不到依次退而抢 CANCELLING/PAUSING→TERMINATED：cancel() 已先行 CAS 到 CANCELLING 发
                    // EXECUTION_CANCELED；pause() 已先行 CAS 到 PAUSING 发 EXECUTION_PAUSED（AAF-110，责任主体
                    // 不变，状态槎不删除）——两者都不能被误判为失败终态。
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
                                                            : pauseWonRace(active)
                                                                    ? pausedEvent(
                                                                            command,
                                                                            execution
                                                                                    .agentIdentifier(),
                                                                            mappingState,
                                                                            active,
                                                                            stateUserKey)
                                                                    : eventMapper.failure(
                                                                            command,
                                                                            execution
                                                                                    .agentIdentifier(),
                                                                            mappingState,
                                                                            failure)))
                    // 正常完成路径：同一套仲裁。抢到 ACTIVE→TERMINATED 即静默结束；
                    // 抢不到依次退而抢 CANCELLING/PAUSING→TERMINATED，补发对应终态事件——不会与上面的
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
                                                            : pauseWonRace(active)
                                                                    ? Flux.just(
                                                                            pausedEvent(
                                                                                    command,
                                                                                    execution
                                                                                            .agentIdentifier(),
                                                                                    mappingState,
                                                                                    active,
                                                                                    stateUserKey))
                                                                    : Flux.empty()))
                    // concatMap 保证串行入库，sequence 由存储层原子分配；已由 HITL 事务提交的 exact
                    // canonical event 只回送当前 Flux，不再次 append
                    .concatMap(
                            event -> {
                                if (active.alreadyPersistedEventIds()
                                        .remove(event.eventId().value())) {
                                    return Mono.just(event);
                                }
                                return eventStore
                                        .append(event, command.context().lease())
                                        .timeout(policy.persistTimeout());
                            })
                    .doFinally(ignored -> release(executionId, active, command, stateUserKey));
        } catch (RuntimeException failure) {
            release(executionId, active, command, stateUserKey);
            throw failure;
        }
    }

    private Mono<SourceEventReceipt> prepareSourceEvent(
            AgentEvent event,
            ActiveExecution active,
            AgentExecutionCommand command,
            ResolvedExecution execution,
            String stateUserKey) {
        var interrupt = committedInterruptEvidence(event, command);
        if (interrupt.isPresent()) {
            var committed = interrupt.orElseThrow();
            return loadCommittedInterruptEvent(command, committed)
                    .publishOn(blockingScheduler)
                    .map(
                            stored -> {
                                preserveHitlState(
                                        committed.waitType(), active, command, stateUserKey);
                                var consumed =
                                        evidenceStore
                                                .take(
                                                        command.context().executionId(),
                                                        committed.toolCallId())
                                                .orElseThrow(
                                                        () ->
                                                                new IllegalStateException(
                                                                        "已确认的 canonical interrupt evidence 在回送前丢失"));
                                if (!consumed.equals(committed.values())) {
                                    throw new IllegalStateException(
                                            "canonical interrupt evidence 在回送期间发生变化");
                                }
                                onSourceEvent(event, active);
                                meteringObserver.observe(event, execution.model(), command);
                                return new SourceEventReceipt(event, stored);
                            });
        }
        return Mono.fromCallable(
                        () -> {
                            if (GUARDED_EVENT_TYPES.contains(event.getType())) {
                                requireCurrent(command);
                            }
                            onSourceEvent(event, active);
                            meteringObserver.observe(event, execution.model(), command);
                            return new SourceEventReceipt(event, null);
                        })
                .subscribeOn(blockingScheduler);
    }

    private Optional<CommittedInterruptEvidence> committedInterruptEvidence(
            AgentEvent event, AgentExecutionCommand command) {
        if (!(event instanceof ToolResultEndEvent toolResult)
                || toolResult.getState() == ToolResultState.SUCCESS) {
            return Optional.empty();
        }
        var evidence =
                evidenceStore
                        .peek(command.context().executionId(), toolResult.getToolCallId())
                        .orElseGet(Map::of);
        var authorizationRequired = Boolean.TRUE.equals(evidence.get("authorizationRequired"));
        var clarificationRequired = Boolean.TRUE.equals(evidence.get("clarificationRequired"));
        if (authorizationRequired && clarificationRequired) {
            throw new IllegalStateException("同一工具结果不能同时请求授权和结构化澄清");
        }
        var approvalId = Objects.toString(evidence.get("approvalId"), "");
        if (authorizationRequired && !approvalId.isBlank()) {
            return Optional.of(
                    new CommittedInterruptEvidence(
                            toolResult.getToolCallId(),
                            approvalId,
                            "approval-request-" + approvalId,
                            "approvalId",
                            ExecutionEventType.AUTHORIZATION_REQUESTED,
                            ExecutionEventStatus.AWAITING_AUTHORIZATION,
                            OwnerType.SYSTEM,
                            evidence));
        }
        var requestId = Objects.toString(evidence.get("requestId"), "");
        if (clarificationRequired && !requestId.isBlank()) {
            return Optional.of(
                    new CommittedInterruptEvidence(
                            toolResult.getToolCallId(),
                            requestId,
                            "clarification-request-" + requestId,
                            "requestId",
                            ExecutionEventType.CLARIFICATION_REQUESTED,
                            ExecutionEventStatus.AWAITING_CLARIFICATION,
                            OwnerType.ASSISTANT,
                            evidence));
        }
        return Optional.empty();
    }

    private Mono<ExecutionEvent> loadCommittedInterruptEvent(
            AgentExecutionCommand command, CommittedInterruptEvidence interrupt) {
        var context = command.context();
        return eventStore
                .readExecution(context.tenantId(), context.executionId(), command.sequenceBase())
                .filter(event -> interrupt.eventId().equals(event.eventId().value()))
                .singleOrEmpty()
                .switchIfEmpty(
                        Mono.error(
                                new IllegalStateException(
                                        "canonical interrupt evidence 缺少已持久化事件: "
                                                + interrupt.eventId())))
                .map(
                        event -> {
                            requireCommittedInterruptEvent(event, command, interrupt);
                            return event;
                        });
    }

    private static void requireCommittedInterruptEvent(
            ExecutionEvent event,
            AgentExecutionCommand command,
            CommittedInterruptEvidence interrupt) {
        var context = command.context();
        if (!event.eventId().value().equals(interrupt.eventId())
                || !event.tenantId().equals(context.tenantId())
                || !event.conversationId().equals(context.conversationId())
                || !event.sessionId().equals(context.sessionId())
                || !Objects.equals(event.taskId(), context.taskId())
                || !event.executionId().equals(context.executionId())
                || !event.runId().equals(context.runId())
                || !Objects.equals(event.parentExecutionId(), context.parentExecutionId())
                || event.type() != interrupt.waitType()
                || event.status() != interrupt.waitStatus()
                || event.controlMode() != context.controlMode()
                || event.ownerType() != interrupt.ownerType()
                || !Objects.equals(event.assistantId(), context.assistantId())
                || !event.userId().equals(context.userId())
                || !event.correlationId().equals(context.correlationId())
                || !Objects.equals(event.causationId(), context.causationId())
                || !Objects.equals(event.idempotencyKey(), context.idempotencyKey())
                || !Objects.equals(event.nodeIdentity(), context.nodeIdentity())
                || !interrupt
                        .interruptId()
                        .equals(
                                Objects.toString(
                                        event.payload()
                                                .values()
                                                .get(interrupt.payloadIdentityKey()),
                                        ""))) {
            throw new IllegalStateException("已持久化 canonical interrupt 与当前 tool/execution 身份不一致");
        }
    }

    private static boolean canonicalWait(ExecutionEvent event) {
        return switch (event.type()) {
            case AUTHORIZATION_REQUESTED ->
                    event.status() == ExecutionEventStatus.AWAITING_AUTHORIZATION;
            case CLARIFICATION_REQUESTED ->
                    event.status() == ExecutionEventStatus.AWAITING_CLARIFICATION;
            default -> false;
        };
    }

    private void preserveHitlState(
            ExecutionEvent event,
            ActiveExecution active,
            AgentExecutionCommand command,
            String stateUserKey) {
        if (!canonicalWait(event)) {
            return;
        }
        preserveHitlState(event.type(), active, command, stateUserKey);
    }

    private void preserveHitlState(
            ExecutionEventType waitType,
            ActiveExecution active,
            AgentExecutionCommand command,
            String stateUserKey) {
        if (active.hitlStateReceipt().get() != null) {
            return;
        }

        HitlStateReceipt receipt;
        try {
            var state = active.runtimeContext().getAgentState();
            if (state == null) {
                throw new IllegalStateException("call-scoped AgentState 尚未绑定");
            }
            if (!stateUserKey.equals(state.getUserId())) {
                throw new IllegalStateException("call-scoped AgentState user key 不匹配");
            }
            if (!command.context().sessionId().value().equals(state.getSessionId())) {
                throw new IllegalStateException("call-scoped AgentState session 不匹配");
            }
            stateStore.saveSuspended(command.context(), state);
            receipt = new HitlStateReceipt(true, waitType, "");
            log.debug(
                    "[AgentLoop] canonical HITL 状态已持久化：executionId={}，waitType={}",
                    command.context().executionId().value(),
                    waitType);
        } catch (RuntimeException failure) {
            receipt = new HitlStateReceipt(false, waitType, failure.getClass().getSimpleName());
            log.warn(
                    "[AgentLoop] canonical HITL 状态保存失败，后续 same-attempt resume 将 fail-closed：executionId={}，waitType={}，错误类型={}",
                    command.context().executionId().value(),
                    waitType,
                    failure.getClass().getSimpleName());
        }
        active.hitlStateReceipt().compareAndSet(null, receipt);
    }

    private ExecutionEvent pausedEvent(
            AgentExecutionCommand command,
            String agentIdentifier,
            MappingState mappingState,
            ActiveExecution active,
            String stateUserKey) {
        PauseStateReceipt receipt;
        try {
            var state = active.runtimeContext().getAgentState();
            if (state == null) {
                throw new IllegalStateException("call-scoped AgentState 尚未绑定");
            }
            if (!stateUserKey.equals(state.getUserId())) {
                throw new IllegalStateException("call-scoped AgentState user key 不匹配");
            }
            stateStore.save(state.getUserId(), state.getSessionId(), "agent_state", state);
            receipt =
                    new PauseStateReceipt(
                            true,
                            command.context().executionId().value(),
                            "agentscope-agent-state-v1",
                            Instant.now(),
                            "");
        } catch (RuntimeException failure) {
            receipt =
                    new PauseStateReceipt(
                            false,
                            command.context().executionId().value(),
                            "agentscope-agent-state-v1",
                            null,
                            failure.getClass().getSimpleName());
            log.warn(
                    "[AgentLoop] 暂停状态保存失败，将由 durable pause 降级 fresh attempt：executionId={}，错误类型={}",
                    command.context().executionId().value(),
                    failure.getClass().getSimpleName());
        }
        active.pauseReceipt().set(receipt);
        var values = new java.util.LinkedHashMap<String, Object>();
        values.put("pauseStateSaved", receipt.saved());
        values.put("pauseStateSlotId", receipt.stateSlotId());
        values.put("pauseStateSchema", receipt.stateSchema());
        values.put(
                "pauseStateSavedAt", receipt.savedAt() == null ? "" : receipt.savedAt().toString());
        values.put("pauseStateFailure", receipt.failure());
        return eventMapper.paused(
                command,
                agentIdentifier,
                mappingState,
                new ExecutionEventPayload(Map.copyOf(values)));
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
            if (command.resumeStateRequired()) {
                throw new ContextBudgetExceededException(
                        "same-attempt resume 缺少预期 AgentScope 状态，拒绝静默降级 fresh attempt");
            }
            return;
        }
        var persisted = state.orElseThrow();
        if (command.resumeStateRequired()) {
            return;
        }
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

    /**
     * AGENT_START 之后 Agent 才可被中断，因此在此补发早于启动到达的取消请求；同时把 call-scoped {@code AgentState}（框架在 call
     * 入口才注入到 {@code RuntimeContext}，建立 {@code active} 时读不到）绑定进 {@code
     * GracefulShutdownManager}（AAF-110 #11004），使优雅停机时精确定位到本次调用的 session。
     */
    private void onSourceEvent(AgentEvent event, ActiveExecution active) {
        if (event.getType() != AgentEventType.AGENT_START) {
            return;
        }
        active.started().set(true);
        interruptIfCancelledAndStarted(active);
        var requestId = active.shutdownRequestId().get();
        var state = active.runtimeContext().getAgentState();
        if (requestId != null && state != null) {
            GracefulShutdownManager.getInstance().bindRequestState(requestId, state);
        }
    }

    private void interruptIfCancelledAndStarted(ActiveExecution active) {
        if ((active.terminal().get() == TerminalState.CANCELLING
                        || active.terminal().get() == TerminalState.PAUSING)
                && active.started().get()) {
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
        if (active.terminal().compareAndSet(TerminalState.CANCELLING, TerminalState.TERMINATED)) {
            active.cancelWon().set(true);
            return true;
        }
        return false;
    }

    /** 与 {@link #cancelWonRace} 平级：{@code pause(ExecutionId)} 版本的终态仲裁（AAF-110）。 */
    private static boolean pauseWonRace(ActiveExecution active) {
        if (active.terminal().compareAndSet(TerminalState.PAUSING, TerminalState.TERMINATED)) {
            active.pauseWon().set(true);
            return true;
        }
        return false;
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
     *   <li>本 attempt 的 AgentScope 状态槽 stale-safe 删除，避免 Redis 无界增长
     * </ul>
     *
     * <p><b>close 不是空操作</b>：core {@code ReActAgent.close()} 会 {@code unbindStateSaver} 并清空本地 {@code
     * stateCache}。而 {@code interrupt(ctx)} 依赖该缓存定位在飞调用，因此 close 仍只允许发生在订阅终止后的 {@code doFinally}。
     *
     * <p>状态槽清理失败不影响执行结论；清理会重验原 Dispatch 仍为最新代，绝不越过 same-attempt resume 删除新状态。
     */
    private void release(
            ExecutionId executionId,
            ActiveExecution active,
            AgentExecutionCommand command,
            String stateUserKey) {
        release(executionId, active);
        evidenceStore.clear(executionId);
        // 无论后续是否跳过状态槎删除，都要释放 GracefulShutdownManager 的追踪，避免 activeRequestsById 无界增长
        // （AAF-110 #11004）。
        var requestId = active.shutdownRequestId().get();
        if (requestId != null) {
            GracefulShutdownManager.getInstance().unregisterRequest(requestId);
        }
        if (!active.stateAccessReady().get()) {
            closeStateRegistration(active);
            return;
        }
        var pauseReceipt = active.pauseReceipt().get();
        if (active.pauseWon().get() && pauseReceipt != null && pauseReceipt.saved()) {
            log.debug(
                    "[AgentLoop] 暂停状态已持久化，跳过状态槎删除：executionId={}，责任主体不变，下次同一 executionId"
                            + " 重新发起可续接对话历史",
                    executionId.value());
            closeStateRegistration(active);
            return;
        }
        var hitlReceipt = active.hitlStateReceipt().get();
        if (!active.cancelWon().get() && hitlReceipt != null && hitlReceipt.saved()) {
            log.debug(
                    "[AgentLoop] canonical HITL 状态已持久化，跳过状态槎删除：executionId={}，waitType={}",
                    executionId.value(),
                    hitlReceipt.waitType());
            closeStateRegistration(active);
            return;
        }
        closeStateRegistration(active);
        deleteExecutionState(command, stateUserKey);
    }

    private static void closeStateRegistration(ActiveExecution active) {
        var registration = active.stateRegistration().getAndSet(null);
        if (registration != null) {
            registration.close();
        }
    }

    /** 状态槽清理走阻塞调度器：doFinally 可能运行在 AgentScope 事件线程上，Redis 删除不能占用它。 */
    private void deleteExecutionState(AgentExecutionCommand command, String stateUserKey) {
        blockingScheduler.schedule(
                () -> {
                    try {
                        stateStore.deleteStaleSafe(
                                command.context(),
                                stateUserKey,
                                command.context().sessionId().value());
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
        /**
         * 暂停触发的终止（AAF-110）：与 {@code CANCELLING} 语义不同——责任主体不变，调用方期待下次同一 {@code executionId}
         * 能续接对话历史。{@code doFinally} 据此跳过状态槎删除，不进入 {@code CANCELLING} 的常规取消收尾路径。
         */
        PAUSING,
        TERMINATED
    }

    private record SourceEventReceipt(AgentEvent sourceEvent, ExecutionEvent committedEvent) {}

    private record CommittedInterruptEvidence(
            String toolCallId,
            String interruptId,
            String eventId,
            String payloadIdentityKey,
            ExecutionEventType waitType,
            ExecutionEventStatus waitStatus,
            OwnerType ownerType,
            Map<String, Object> values) {}

    private record PauseStateReceipt(
            boolean saved,
            String stateSlotId,
            String stateSchema,
            Instant savedAt,
            String failure) {}

    private record HitlStateReceipt(boolean saved, ExecutionEventType waitType, String failure) {}

    /** 单次执行的运行态标志位，用于取消、中断与终态事件去重。 */
    private record ActiveExecution(
            ReActAgent agent,
            RuntimeContext runtimeContext,
            boolean ephemeral,
            AtomicReference<Registration> stateRegistration,
            AtomicBoolean stateAccessReady,
            AtomicBoolean released,
            AtomicBoolean started,
            AtomicBoolean interruptIssued,
            AtomicReference<TerminalState> terminal,
            /** cancelWonRace 成功后置位；取消清理优先于任何已保存的 HITL 状态回执。 */
            AtomicBoolean cancelWon,
            /**
             * {@code pauseWonRace} 成功后置位（AAF-110）——{@code terminal} 最终统一收敛为 {@code TERMINATED}，
             * 无法反推仲裁路径，需要独立标志供 {@code release(...)} 判断是否跳过状态槎删除。
             */
            AtomicBoolean pauseWon,
            AtomicReference<PauseStateReceipt> pauseReceipt,
            /** canonical HITL waiting 事件的状态保存结果；只有 saved=true 才允许 release 保留状态槽。 */
            AtomicReference<HitlStateReceipt> hitlStateReceipt,
            /** 已由 durable transition 原子持久化、只需回送当前 Flux 的 exact eventId。 */
            Set<String> alreadyPersistedEventIds,
            /**
             * {@code GracefulShutdownManager.registerRequest(agent)} 返回的请求标识（AAF-110 #11004），
             * 建立时为空，AGENT_START 到达后才能拿到 call-scoped AgentState 并绑定。
             */
            AtomicReference<String> shutdownRequestId) {

        private ActiveExecution(
                ReActAgent agent, RuntimeContext runtimeContext, boolean ephemeral) {
            this(
                    agent,
                    runtimeContext,
                    ephemeral,
                    new AtomicReference<>(),
                    new AtomicBoolean(),
                    new AtomicBoolean(),
                    new AtomicBoolean(),
                    new AtomicBoolean(),
                    new AtomicReference<>(TerminalState.ACTIVE),
                    new AtomicBoolean(),
                    new AtomicBoolean(),
                    new AtomicReference<>(),
                    new AtomicReference<>(),
                    ConcurrentHashMap.newKeySet(),
                    new AtomicReference<>());
        }
    }
}
