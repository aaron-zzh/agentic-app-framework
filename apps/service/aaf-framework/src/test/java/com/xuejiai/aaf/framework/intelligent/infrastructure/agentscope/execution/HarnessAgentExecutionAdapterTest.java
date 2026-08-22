package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.execution;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    @Mock private HarnessAgent agent;
    @Mock private ReActAgent delegate;
    @Mock private InvocationContext invocationContext;
    @Mock private ExecutionEvent failureEvent;

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
                        delegatedTasks);
        runtimeContext = RuntimeContext.builder().build();
        executionModel = new ModelSpec("1");
        when(invocationContext.controlMode()).thenReturn(ControlMode.READ_ONLY);
        when(invocationContext.executionId()).thenReturn(new ExecutionId("execution-test"));
        when(invocationContext.runId()).thenReturn(new RunId("run-test"));
        when(invocationContext.sessionId()).thenReturn(new SessionId("session-test"));
        when(contextMapper.stateUserKey(invocationContext)).thenReturn("state-user");
        when(stateStore.get("state-user", "session-test", "agent_state", AgentState.class))
                .thenReturn(Optional.empty());
        when(contextMapper.toAgentScope(invocationContext)).thenReturn(runtimeContext);
        when(messageMapper.toAgentScope(anyList())).thenReturn(List.of());
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

    private AgentExecutionCommand command(Duration timeout) {
        var dynamic =
                new SubagentSpec.Dynamic(
                        "agent.dynamic-lifecycle",
                        "验证临时生命周期",
                        "只执行测试任务",
                        List.of(),
                        new ExecutionPolicy(3, 1, timeout, 128000),
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
                                        List.of())),
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
