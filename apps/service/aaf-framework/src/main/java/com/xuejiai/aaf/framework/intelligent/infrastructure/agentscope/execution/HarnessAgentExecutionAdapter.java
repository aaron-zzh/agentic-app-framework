package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.execution;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentExecutionPort;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.compiler.AgentScopeSpecCompiler;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeEventMapper;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeEventMapper.MappingState;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeMessageMapper;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeRuntimeContextMapper;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.harness.agent.HarnessAgent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** AgentExecutionPort 的唯一 AgentScope Harness 实现。 */
public final class HarnessAgentExecutionAdapter implements AgentExecutionPort {

    private final AgentDefinitionPort definitions;
    private final AgentScopeSpecCompiler compiler;
    private final AgentScopeMessageMapper messageMapper;
    private final AgentScopeRuntimeContextMapper contextMapper;
    private final AgentScopeEventMapper eventMapper;
    private final ConcurrentMap<ExecutionId, ActiveExecution> activeExecutions =
            new ConcurrentHashMap<>();

    public HarnessAgentExecutionAdapter(
            AgentDefinitionPort definitions,
            AgentScopeSpecCompiler compiler,
            AgentScopeMessageMapper messageMapper,
            AgentScopeRuntimeContextMapper contextMapper,
            AgentScopeEventMapper eventMapper) {
        this.definitions = Objects.requireNonNull(definitions, "definitions 不能为空");
        this.compiler = Objects.requireNonNull(compiler, "compiler 不能为空");
        this.messageMapper = Objects.requireNonNull(messageMapper, "messageMapper 不能为空");
        this.contextMapper = Objects.requireNonNull(contextMapper, "contextMapper 不能为空");
        this.eventMapper = Objects.requireNonNull(eventMapper, "eventMapper 不能为空");
    }

    @Override
    public Flux<ExecutionEvent> execute(AgentExecutionCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        return Flux.defer(() -> executeDeferred(command));
    }

    @Override
    public Mono<Boolean> cancel(ExecutionId executionId) {
        Objects.requireNonNull(executionId, "executionId 不能为空");
        return Mono.fromSupplier(
                () -> {
                    var active = activeExecutions.get(executionId);
                    if (active == null
                            || active.sourceCompleted().get()
                            || !active.cancelled().compareAndSet(false, true)) {
                        return false;
                    }
                    interruptIfCancelledAndStarted(active);
                    return true;
                });
    }

    private Flux<ExecutionEvent> executeDeferred(AgentExecutionCommand command) {
        var mappingState = new MappingState(command.sequenceBase());
        try {
            var spec =
                    definitions
                            .findByIdAndVersion(command.agentId(), command.definitionVersion())
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Agent 定义不存在: "
                                                            + command.agentId().value()
                                                            + "@"
                                                            + command.definitionVersion()));
            var agent = compiler.compile(spec);
            var runtimeContext = contextMapper.toAgentScope(command.context());
            var active = new ActiveExecution(agent, runtimeContext);
            var existing = activeExecutions.putIfAbsent(command.context().executionId(), active);
            if (existing != null) {
                return Flux.error(
                        new IllegalStateException(
                                "executionId 已存在活跃执行: "
                                        + command.context().executionId().value()));
            }

            return agent.streamEvents(
                            messageMapper.toAgentScope(command.messages()), runtimeContext)
                    .doOnNext(event -> onSourceEvent(event, active))
                    .concatMap(
                            event ->
                                    Mono.justOrEmpty(
                                            eventMapper.map(event, command, mappingState)))
                    .timeout(spec.executionPolicy().timeout())
                    .doOnError(ignored -> interruptOnce(active))
                    .onErrorResume(
                            failure -> {
                                if (active.cancelled().get()) {
                                    active.cancellationEmitted().set(true);
                                    return Flux.just(
                                            eventMapper.canceled(command, mappingState));
                                }
                                return Flux.just(
                                        eventMapper.failure(command, mappingState, failure));
                            })
                    .doOnComplete(() -> active.sourceCompleted().set(true))
                    .concatWith(
                            Flux.defer(
                                    () ->
                                            active.cancelled().get()
                                                            && active.cancellationEmitted()
                                                                    .compareAndSet(false, true)
                                                    ? Flux.just(
                                                            eventMapper.canceled(
                                                                    command, mappingState))
                                                    : Flux.empty()))
                    .doFinally(
                            ignored ->
                                    activeExecutions.remove(
                                            command.context().executionId(), active));
        } catch (RuntimeException failure) {
            return Flux.just(eventMapper.failure(command, mappingState, failure));
        }
    }

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

    private void interruptOnce(ActiveExecution active) {
        if (active.interruptIssued().compareAndSet(false, true)) {
            active.agent().getDelegate().interrupt(active.runtimeContext());
        }
    }

    private record ActiveExecution(
            HarnessAgent agent,
            RuntimeContext runtimeContext,
            AtomicBoolean started,
            AtomicBoolean cancelled,
            AtomicBoolean interruptIssued,
            AtomicBoolean cancellationEmitted,
            AtomicBoolean sourceCompleted) {

        private ActiveExecution(HarnessAgent agent, RuntimeContext runtimeContext) {
            this(
                    agent,
                    runtimeContext,
                    new AtomicBoolean(),
                    new AtomicBoolean(),
                    new AtomicBoolean(),
                    new AtomicBoolean(),
                    new AtomicBoolean());
        }
    }
}
