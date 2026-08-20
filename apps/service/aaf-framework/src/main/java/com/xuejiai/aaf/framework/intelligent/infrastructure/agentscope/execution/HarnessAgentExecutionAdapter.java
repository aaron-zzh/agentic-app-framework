package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.execution;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand;
import com.xuejiai.aaf.framework.intelligent.agent.model.ExecutionPolicy;
import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentExecutionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextBudgetExceededException;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.compiler.AgentScopeSpecCompiler;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeEventMapper;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeEventMapper.MappingState;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeMessageMapper;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeRuntimeContextMapper;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.middleware.AgentScopeTokenMeteringObserver;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AgentExecutionPort 的唯一 AgentScope Harness 实现。
 *
 * <p>职责：解析执行规格 → 编译 HarnessAgent → 订阅 {@code streamEvents} 事件流 → 映射为 AAF 事件并顺序入库。同时维护活跃执行表以支持取消与中断。
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

    /** 活跃执行表：executionId → 运行态句柄，cancel 依赖它定位 Agent 与 RuntimeContext。 */
    private final ConcurrentMap<ExecutionId, ActiveExecution> activeExecutions =
            new ConcurrentHashMap<>();

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
            DelegatedTaskPort delegatedTasks) {
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
    }

    /** 订阅时才真正解析与编译，保证每次 subscribe 都是独立执行。 */
    @Override
    public Flux<ExecutionEvent> execute(AgentExecutionCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        return Flux.defer(() -> executeDeferred(command));
    }

    /** 取消：只在首次调用且源流未结束时生效，Agent 已启动才能下发 interrupt。 */
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
                    if (active.sourceCompleted().get()) {
                        log.debug(
                                "[AgentLoop] 忽略中断请求：executionId={}，原因=事件流已结束", executionId.value());
                        return false;
                    }
                    if (!active.cancelled().compareAndSet(false, true)) {
                        log.debug(
                                "[AgentLoop] 忽略中断请求：executionId={}，原因=已标记取消", executionId.value());
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

    private Flux<ExecutionEvent> executeResolved(
            AgentExecutionCommand command, MappingState mappingState, ResolvedExecution execution) {
        var executionId = command.context().executionId();
        final RuntimeContext runtimeContext;
        try {
            runtimeContext = contextMapper.toAgentScope(command.context());
            requireNoHiddenPersistentHistory(command);
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
                return Flux.error(
                        new IllegalStateException("executionId 已存在活跃执行: " + executionId.value()));
            }
            log.debug(
                    "[AgentLoop] 已注册活跃执行并订阅 Harness 事件流：executionId={}，agent={}，临时实例={}",
                    executionId.value(),
                    execution.agentIdentifier(),
                    execution.ephemeral());

            return execution
                    .agent()
                    .streamEvents(messageMapper.toAgentScope(command.messages()), runtimeContext)
                    // 每个源事件：校验租约仍然有效 → 记录启动态 → 计量 token
                    .doOnNext(
                            event -> {
                                requireCurrent(command);
                                onSourceEvent(event, active);
                                meteringObserver.observe(event, execution.model(), command);
                            })
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
                    .timeout(execution.executionPolicy().timeout())
                    .doOnError(ignored -> interruptOnce(active))
                    // 异常收口：已取消发 EXECUTION_CANCELED，否则发 RUN_FAILED，流始终正常结束
                    .onErrorResume(
                            failure -> {
                                if (active.cancelled().get()) {
                                    active.cancellationEmitted().set(true);
                                    return Flux.just(
                                            eventMapper.canceled(
                                                    command,
                                                    execution.agentIdentifier(),
                                                    mappingState));
                                }
                                return Flux.just(
                                        eventMapper.failure(
                                                command,
                                                execution.agentIdentifier(),
                                                mappingState,
                                                failure));
                            })
                    .doOnComplete(() -> active.sourceCompleted().set(true))
                    // 取消与正常完成竞态时补一条终态事件，CAS 保证只发一次
                    .concatWith(
                            Flux.defer(
                                    () ->
                                            active.cancelled().get()
                                                            && active.cancellationEmitted()
                                                                    .compareAndSet(false, true)
                                                    ? Flux.just(
                                                            eventMapper.canceled(
                                                                    command,
                                                                    execution.agentIdentifier(),
                                                                    mappingState))
                                                    : Flux.empty()))
                    // concatMap 保证串行入库，sequence 由存储层原子分配
                    .concatMap(event -> eventStore.append(event, command.context().lease()))
                    .doFinally(ignored -> release(executionId, active));
        } catch (RuntimeException failure) {
            release(executionId, active);
            throw failure;
        }
    }

    /** AgentScope 会先恢复 agent_state 再追加 command messages；隐藏历史未纳入冻结画像时必须拒绝。 */
    private void requireNoHiddenPersistentHistory(AgentExecutionCommand command) {
        var context = command.context();
        final java.util.Optional<AgentState> state;
        try {
            state =
                    stateStore.get(
                            contextMapper.stateUserKey(context),
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
        if (active.cancelled().get() && active.started().get()) {
            interruptOnce(active);
        }
    }

    /** interrupt 幂等：CAS 保证同一执行只向 ReActAgent 下发一次中断。 */
    private void interruptOnce(ActiveExecution active) {
        if (active.interruptIssued().compareAndSet(false, true)) {
            active.agent().getDelegate().interrupt(active.runtimeContext());
            log.debug("[AgentLoop] 已向 Harness ReAct 执行体下发中断");
        }
    }

    /** 出表并释放一次性实例；缓存实例由 compiler 统一管理，不在此关闭。 */
    private void release(ExecutionId executionId, ActiveExecution active) {
        activeExecutions.remove(executionId, active);
        if (active.ephemeral() && active.released().compareAndSet(false, true)) {
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
            HarnessAgent agent,
            ModelSpec model,
            String agentIdentifier,
            ExecutionPolicy executionPolicy,
            boolean ephemeral) {}

    /** 单次执行的运行态标志位，用于取消、中断与终态事件去重。 */
    private record ActiveExecution(
            HarnessAgent agent,
            RuntimeContext runtimeContext,
            boolean ephemeral,
            AtomicBoolean released,
            AtomicBoolean started,
            AtomicBoolean cancelled,
            AtomicBoolean interruptIssued,
            AtomicBoolean cancellationEmitted,
            AtomicBoolean sourceCompleted) {

        private ActiveExecution(
                HarnessAgent agent, RuntimeContext runtimeContext, boolean ephemeral) {
            this(
                    agent,
                    runtimeContext,
                    ephemeral,
                    new AtomicBoolean(),
                    new AtomicBoolean(),
                    new AtomicBoolean(),
                    new AtomicBoolean(),
                    new AtomicBoolean(),
                    new AtomicBoolean());
        }
    }
}
