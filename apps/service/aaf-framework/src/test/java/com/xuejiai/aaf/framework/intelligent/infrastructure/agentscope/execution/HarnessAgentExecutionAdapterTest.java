package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.intelligent.agent.model.ActivatedSkill;
import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand;
import com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage;
import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizedSkillSummary;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt.PromptLayerKind;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt.PromptLayerSource;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt.PromptSourceKind;
import com.xuejiai.aaf.framework.intelligent.agent.model.ExecutionPolicy;
import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.SkillExecutionProfile;
import com.xuejiai.aaf.framework.intelligent.agent.model.SkillSelectionManifest;
import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillActivationMode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillScope;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillSelectionMode;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillVersionRef;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.compiler.AgentScopeSpecCompiler;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeEventMapper;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeMessageMapper;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeRuntimeContextMapper;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.middleware.AgentScopeTokenMeteringObserver;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool.ToolResultEvidenceStore;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

class HarnessAgentExecutionAdapterTest extends BaseMockitoUnitTest {

    @Mock private AgentDefinitionPort definitions;
    @Mock private AgentScopeSpecCompiler compiler;
    @Mock private AgentScopeMessageMapper messageMapper;
    @Mock private AgentScopeRuntimeContextMapper contextMapper;
    @Mock private AgentStateStore stateStore;
    @Mock private AgentScopeEventMapper eventMapper;
    @Mock private AgentScopeTokenMeteringObserver meteringObserver;
    @Mock private ExecutionEventStorePort eventStore;
    @Mock private ConversationLeasePort leases;
    @Mock private DelegatedTaskPort delegatedTasks;
    @Mock private ToolResultEvidenceStore evidenceStore;
    @Mock private HarnessAgent agent;
    @Mock private ReActAgent delegate;
    @Mock private InvocationContext invocationContext;
    @Mock private ExecutionEvent failureEvent;
    @Mock private ExecutionEvent canceledEvent;
    @Mock private ExecutionEvent runningEvent;
    @Mock private ExecutionEvent settledEvent;

    private HarnessAgentExecutionAdapter adapter;
    private RuntimeContext runtimeContext;
    private ModelSpec executionModel;

    @BeforeEach
    void setUp() {
        adapter =
                new HarnessAgentExecutionAdapter(
                        definitions,
                        compiler,
                        messageMapper,
                        contextMapper,
                        stateStore,
                        eventMapper,
                        meteringObserver,
                        eventStore,
                        leases,
                        delegatedTasks,
                        evidenceStore,
                        // 单测用 immediate 调度器：把校验保持在调用线程上，取消与完成的时序才是确定的
                        Schedulers.immediate());
        runtimeContext = RuntimeContext.builder().build();
        executionModel = new ModelSpec("1");
        // 公共桩为 lenient：重放守卫命中的用例根本不会走到编译与状态校验，不应因未用到公共桩而失败
        lenient().when(invocationContext.controlMode()).thenReturn(ControlMode.READ_ONLY);
        lenient().when(invocationContext.tenantId()).thenReturn(new TenantId("tenant-test"));
        lenient()
                .when(invocationContext.executionId())
                .thenReturn(new ExecutionId("execution-test"));
        lenient().when(invocationContext.runId()).thenReturn(new RunId("run-test"));
        lenient().when(invocationContext.sessionId()).thenReturn(new SessionId("session-test"));
        lenient()
                .when(contextMapper.stateUserKey(eq(invocationContext), any()))
                .thenReturn("state-user");
        lenient()
                .when(stateStore.get("state-user", "session-test", "agent_state", AgentState.class))
                .thenReturn(Optional.empty());
        lenient()
                .when(contextMapper.toAgentScope(eq(invocationContext), any()))
                .thenReturn(runtimeContext);
        lenient().when(messageMapper.toAgentScope(anyList())).thenReturn(List.of());
        // 默认无历史事件：重放守卫放行，进入真实执行
        lenient().when(eventStore.readExecution(any(), any(), anyLong())).thenReturn(Flux.empty());
    }

    @Test
    @DisplayName("Given Dynamic 执行源失败 When 流终止 Then 关闭临时 Agent")
    void should_close_dynamic_agent_after_source_failure() {
        var command = command(Duration.ofSeconds(30));
        stubTerminalFailure();
        stubDynamicAgent(command, Flux.error(new IllegalStateException("source failure")));

        adapter.execute(command).collectList().block();

        verify(agent).close();
    }

    @Test
    @DisplayName("Given Dynamic 执行超时 When 流终止 Then 关闭临时 Agent")
    void should_close_dynamic_agent_after_timeout() {
        var command = command(Duration.ofMillis(20));
        stubTerminalFailure();
        stubDynamicAgent(command, Flux.never());

        adapter.execute(command).collectList().block();

        verify(agent).close();
    }

    @Test
    @DisplayName("Given Dynamic 执行被订阅方取消 When dispose Then 关闭临时 Agent")
    void should_close_dynamic_agent_after_subscription_cancel() {
        var command = command(Duration.ofSeconds(30));
        stubDynamicAgent(command, Flux.never());

        var subscription = adapter.execute(command).subscribe();
        subscription.dispose();

        verify(agent, timeout(1000)).close();
    }

    /**
     * P0-1（RQ-01）：cancel() 在源完成前 CAS 抢占终态，验证事件列表恰好一次 {@code EXECUTION_CANCELED}，不与自然完成产生双终态。
     *
     * <p>{@code sink.tryEmitComplete()} 在无 Scheduler 边界的情况下同步驱动下游到 {@code doFinally}， 因此在其调用前完成的
     * {@code cancel()} 必然先行完成 CAS，构成确定时序而非概率性竞态。
     */
    @Test
    @DisplayName("Given cancel 在源完成前抢占终态 When 源随后完成 Then 恰好发出一次取消事件")
    void should_emit_canceled_event_exactly_once_when_cancel_wins_before_natural_completion() {
        var command = command(Duration.ofSeconds(30));
        var sink =
                Sinks.many()
                        .multicast()
                        .<io.agentscope.core.event.AgentEvent>onBackpressureBuffer();
        stubDynamicAgent(command, sink.asFlux());
        stubCanceled();
        var results = new CopyOnWriteArrayList<ExecutionEvent>();

        adapter.execute(command).subscribe(results::add);
        sink.tryEmitNext(new AgentStartEvent("session", "reply", "agent"));

        var cancelResult = adapter.cancel(command.context().executionId()).block();
        sink.tryEmitComplete();

        assertThat(cancelResult).isTrue();
        assertThat(results).containsExactly(canceledEvent);
        verify(agent).getDelegate();
        verify(delegate).interrupt(runtimeContext);
    }

    /**
     * P0-1（RQ-01）：源先自然完成，{@code cancel()} 随后调用必须返回 {@code false}，且不产生任何
     * 取消事件——验证"完成胜出时不得出现迟到的取消终态"这条不变量。
     */
    @Test
    @DisplayName("Given 源已自然完成 When 随后调用 cancel Then 返回 false 且不发出取消事件")
    void should_return_false_and_emit_no_canceled_event_after_natural_completion() {
        var command = command(Duration.ofSeconds(30));
        var sink =
                Sinks.many()
                        .multicast()
                        .<io.agentscope.core.event.AgentEvent>onBackpressureBuffer();
        stubDynamicAgent(command, sink.asFlux());
        var results = new CopyOnWriteArrayList<ExecutionEvent>();

        adapter.execute(command).subscribe(results::add);
        sink.tryEmitComplete();

        var cancelResult = adapter.cancel(command.context().executionId()).block();

        assertThat(cancelResult).isFalse();
        assertThat(results).doesNotContain(canceledEvent);
    }

    /**
     * P0-1（RQ-01）：cancel() 先胜出后，源以异常（而非正常完成）终止，{@code onErrorResume} 分支必须
     * 仍然识别已胜出的取消并发出取消事件，不得误判为失败事件——验证异常路径与完成路径共用同一套 仲裁，不会因为终止方式不同而出现不一致的终态类型。
     */
    @Test
    @DisplayName("Given cancel 先胜出 When 源随后以异常终止 Then 仍发出取消事件而非失败事件")
    void should_emit_canceled_event_when_source_errors_after_cancel_won() {
        var command = command(Duration.ofSeconds(30));
        var sink =
                Sinks.many()
                        .multicast()
                        .<io.agentscope.core.event.AgentEvent>onBackpressureBuffer();
        stubDynamicAgent(command, sink.asFlux());
        stubCanceled();
        var results = new CopyOnWriteArrayList<ExecutionEvent>();

        adapter.execute(command).subscribe(results::add);
        var cancelResult = adapter.cancel(command.context().executionId()).block();
        sink.tryEmitError(new IllegalStateException("source failure after cancel"));

        assertThat(cancelResult).isTrue();
        assertThat(results).containsExactly(canceledEvent);
    }

    /** P0-1（RQ-01）对照组：从未调用 cancel 时，源异常必须发出失败事件，确认修复未破坏原有失败路径。 */
    @Test
    @DisplayName("Given 从未调用 cancel When 源以异常终止 Then 发出失败事件")
    void should_emit_failure_event_when_source_errors_without_cancel() {
        var command = command(Duration.ofSeconds(30));
        stubTerminalFailure();
        stubDynamicAgent(command, Flux.error(new IllegalStateException("source failure")));

        var results = adapter.execute(command).collectList().block();

        assertThat(results).containsExactly(failureEvent);
    }

    /**
     * P0-3：cancel() 在 {@code AGENT_START} 到达前调用，验证 interrupt 只在 Agent 真正启动后补发一次，
     * 不会在启动前重复下发、也不会因为标志位竞态而漏发。
     */
    @Test
    @DisplayName("Given cancel 早于 AGENT_START When AGENT_START 到达 Then 补发恰好一次 interrupt")
    void should_interrupt_exactly_once_when_cancel_arrives_before_agent_start() {
        var command = command(Duration.ofSeconds(30));
        var sink =
                Sinks.many()
                        .multicast()
                        .<io.agentscope.core.event.AgentEvent>onBackpressureBuffer();
        stubDynamicAgent(command, sink.asFlux());
        stubCanceled();
        var results = new CopyOnWriteArrayList<ExecutionEvent>();

        adapter.execute(command).subscribe(results::add);
        var cancelResult = adapter.cancel(command.context().executionId()).block();
        sink.tryEmitNext(new AgentStartEvent("session", "reply", "agent"));
        sink.tryEmitComplete();

        assertThat(cancelResult).isTrue();
        verify(delegate, org.mockito.Mockito.times(1)).interrupt(runtimeContext);
    }

    /**
     * RQ-05：同一 {@code executionId} 已存在终态事件时命中重放守卫——不得再次调用模型，只回放已持久事件。
     *
     * <p>崩溃恢复走 {@code asResume} 会保留原 executionId 与 sequenceBase，因此这是恢复链路的真实时序， 不是假设场景。
     */
    @Test
    @DisplayName("Given 执行已有终态事件 When 重复订阅 Then 只回放持久事件且不调用模型")
    void should_replay_persisted_events_and_skip_model_when_execution_already_settled() {
        var command = command(Duration.ofSeconds(30));
        when(settledEvent.ownerType()).thenReturn(OwnerType.AGENT);
        when(settledEvent.type()).thenReturn(ExecutionEventType.RUN_COMPLETED);
        when(eventStore.readExecution(any(), any(), anyLong())).thenReturn(Flux.just(settledEvent));

        var results = adapter.execute(command).collectList().block();

        assertThat(results).containsExactly(settledEvent);
        verify(compiler, org.mockito.Mockito.never())
                .compileDynamic(any(), any(), any(), org.mockito.ArgumentMatchers.anyList());
        verify(agent, org.mockito.Mockito.never()).streamEvents(anyList(), any());
    }

    /** RQ-05：只有非终态历史事件（进程崩溃在中途）时必须继续执行，不能被重放守卫误拦。 */
    @Test
    @DisplayName("Given 历史事件未达终态 When 重新订阅 Then 继续真实执行")
    void should_execute_when_history_has_no_terminal_event() {
        var command = command(Duration.ofSeconds(30));
        when(settledEvent.ownerType()).thenReturn(OwnerType.AGENT);
        when(settledEvent.type()).thenReturn(ExecutionEventType.RUN_STARTED);
        when(eventStore.readExecution(any(), any(), anyLong())).thenReturn(Flux.just(settledEvent));
        stubTerminalFailure();
        stubDynamicAgent(command, Flux.error(new IllegalStateException("source failure")));

        var results = adapter.execute(command).collectList().block();

        assertThat(results).containsExactly(failureEvent);
        verify(agent).streamEvents(anyList(), eq(runtimeContext));
    }

    /**
     * RQ-03：总时限与事件静默时限是两条独立边界。源持续产出已映射事件（每 30ms 一个）永远不会触发静默 超时，但总时限到点后必须仍然终止执行——修复前 timeout
     * 只约束相邻事件间隔，持续输出可无限延长执行。
     */
    @Test
    @DisplayName("Given 源持续产出事件 When 总时限耗尽 Then 仍以时限失败终止")
    void should_terminate_on_total_deadline_even_when_events_keep_arriving() {
        var command =
                command(
                        new ExecutionPolicy(
                                3,
                                1,
                                Duration.ofMillis(300),
                                Duration.ofMillis(300),
                                Duration.ofMillis(300),
                                128000));
        stubTerminalFailure();
        when(eventMapper.map(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(runningEvent));
        when(eventStore.append(eq(runningEvent), eq(null))).thenReturn(Mono.just(runningEvent));
        stubDynamicAgent(
                command,
                Flux.interval(Duration.ofMillis(30))
                        .map(tick -> new AgentStartEvent("session", "reply", "agent")));

        var results = adapter.execute(command).collectList().block(Duration.ofSeconds(10));

        assertThat(results).isNotNull();
        assertThat(results).contains(runningEvent);
        assertThat(results.getLast()).isSameAs(failureEvent);
        var captor = org.mockito.ArgumentCaptor.forClass(Throwable.class);
        verify(eventMapper).failure(any(), any(), any(), captor.capture());
        assertThat(captor.getValue())
                .isInstanceOf(
                        com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping
                                .AgentScopeFailureClassifier.ExecutionDeadlineExceededException
                                .class);
    }

    /** RQ-10 + RQ-08/09：执行终止时必须无条件清理工具证据残留与本次执行私有的状态槽。 */
    @Test
    @DisplayName("Given 执行以失败终止 When 流关闭 Then 清理工具证据与执行状态槽")
    void should_clear_tool_evidence_and_execution_state_on_termination() {
        var command = command(Duration.ofSeconds(30));
        stubTerminalFailure();
        stubDynamicAgent(command, Flux.error(new IllegalStateException("source failure")));

        adapter.execute(command).collectList().block();

        verify(evidenceStore).clear(command.context().executionId());
        verify(stateStore).delete("state-user", "session-test");
    }

    /**
     * RQ-02：租约与任务校验只在边界事件上执行。文本增量不产生外部副作用，逐个 delta 查 Redis + DB 是纯 浪费；本测试锁定"一次执行入口 + 一次 AGENT_START
     * = 2 次校验，delta 不额外增加"。
     */
    @Test
    @DisplayName("Given 委托态源事件含文本增量 When 执行 Then 只在边界事件校验租约")
    void should_guard_only_boundary_events_for_delegated_execution() {
        when(invocationContext.controlMode()).thenReturn(ControlMode.DELEGATED);
        when(invocationContext.lease()).thenReturn(null);
        var command = command(Duration.ofSeconds(30));
        stubDynamicAgent(
                command,
                Flux.just(
                        new AgentStartEvent("session", "reply", "agent"),
                        new TextBlockDeltaEvent("reply", "block", "增量一"),
                        new TextBlockDeltaEvent("reply", "block", "增量二")));

        adapter.execute(command).collectList().block();

        verify(leases, org.mockito.Mockito.times(2)).requireCurrent(null);
        verify(delegatedTasks, org.mockito.Mockito.times(2))
                .requireAgentExecution(invocationContext);
    }

    private void stubCanceled() {
        when(agent.getDelegate()).thenReturn(delegate);
        when(eventMapper.canceled(any(), any(), any())).thenReturn(canceledEvent);
        when(eventStore.append(eq(canceledEvent), eq(null))).thenReturn(Mono.just(canceledEvent));
    }

    private AgentExecutionCommand command(Duration timeout) {
        return command(ExecutionPolicy.withDefaultTimeouts(3, 1, timeout, 128000));
    }

    private AgentExecutionCommand command(ExecutionPolicy policy) {
        var dynamic =
                new SubagentSpec.Dynamic(
                        "agent.dynamic-lifecycle",
                        "验证临时生命周期",
                        "只执行测试任务",
                        List.of(),
                        policy,
                        com.xuejiai.aaf.framework.intelligent.agent.model.ModelSelectionRequirement
                                .balanced(),
                        false);
        var skillVersion = new SkillVersionRef(1L, 1L, 1);
        var selection =
                new SkillSelectionManifest(
                        "test.route",
                        "test.skill",
                        SkillSelectionMode.FIXED,
                        1,
                        List.of(
                                new AuthorizedSkillSummary(
                                        "test.skill",
                                        "测试技能",
                                        "验证临时生命周期",
                                        SkillScope.SYSTEM,
                                        SkillActivationMode.ON_DEMAND,
                                        List.of(),
                                        Set.of(),
                                        Set.of())));
        var skillExecutionProfile =
                new SkillExecutionProfile(
                        selection,
                        List.of(
                                new ActivatedSkill(
                                        "test.skill",
                                        skillVersion,
                                        SkillScope.SYSTEM,
                                        SkillActivationMode.ON_DEMAND,
                                        "技能提示",
                                        Set.of(),
                                        Set.of(),
                                        List.of(),
                                        List.of(),
                                        false)),
                        List.of());
        return new AgentExecutionCommand(
                dynamic,
                Optional.of(
                        new AgentExecutionCommand.RoleAssignment(
                                "system.role.test", "测试角色", List.of("执行测试"), List.of("越权操作"))),
                AgentExecutionCommand.ExecutionMode.DELEGATE,
                Optional.of(executionModel),
                skillExecutionProfile,
                compiled(dynamic.identifier()),
                0,
                List.of(new AgentMessage("message-1", AgentMessage.Role.USER, "执行")),
                invocationContext);
    }

    private static CompiledSystemPrompt compiled(String identity) {
        return CompiledSystemPrompt.compile(
                List.of(
                        new PromptLayerSource(
                                PromptLayerKind.CONSTITUTION,
                                PromptSourceKind.ENGINE_TEMPLATE,
                                CompiledSystemPrompt.CONSTITUTION_NAME,
                                "1",
                                "测试 Constitution"),
                        new PromptLayerSource(
                                PromptLayerKind.IDENTITY,
                                PromptSourceKind.AAF_POLICY,
                                identity,
                                "1",
                                "测试执行身份"),
                        new PromptLayerSource(
                                PromptLayerKind.INVOCATION_POLICY,
                                PromptSourceKind.AAF_POLICY,
                                "invocation:test",
                                "1",
                                "测试调用策略"),
                        new PromptLayerSource(
                                PromptLayerKind.PERSONA,
                                PromptSourceKind.ASSISTANT_PERSONA,
                                "persona:test",
                                "1",
                                "测试 Persona")));
    }

    private void stubTerminalFailure() {
        when(agent.getDelegate()).thenReturn(delegate);
        when(eventStore.append(eq(failureEvent), eq(null))).thenReturn(Mono.just(failureEvent));
        when(eventMapper.failure(any(), any(), any(), any())).thenReturn(failureEvent);
    }

    private void stubDynamicAgent(
            AgentExecutionCommand command, Flux<io.agentscope.core.event.AgentEvent> events) {
        when(compiler.compileDynamic(
                        (SubagentSpec.Dynamic) command.subagentSpec(),
                        executionModel,
                        command.compiledSystemPrompt(),
                        command.skillExecutionProfile().effectiveTools()))
                .thenReturn(agent);
        when(agent.streamEvents(anyList(), eq(runtimeContext))).thenReturn(events);
    }
}
