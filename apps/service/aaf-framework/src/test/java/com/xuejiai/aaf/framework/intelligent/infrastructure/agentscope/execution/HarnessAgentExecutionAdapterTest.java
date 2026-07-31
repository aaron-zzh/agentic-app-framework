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

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand;
import com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage;
import com.xuejiai.aaf.framework.intelligent.agent.model.ExecutionPolicy;
import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.compiler.AgentScopeSpecCompiler;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeEventMapper;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeMessageMapper;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeRuntimeContextMapper;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.middleware.AgentScopeTokenMeteringObserver;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.HarnessAgent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class HarnessAgentExecutionAdapterTest extends BaseMockitoUnitTest {

    @Mock private AgentDefinitionPort definitions;
    @Mock private AgentScopeSpecCompiler compiler;
    @Mock private AgentScopeMessageMapper messageMapper;
    @Mock private AgentScopeRuntimeContextMapper contextMapper;
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
                        eventMapper,
                        meteringObserver,
                        eventStore,
                        leases,
                        delegatedTasks);
        runtimeContext = RuntimeContext.builder().build();
        executionModel = new ModelSpec("1");
        when(invocationContext.controlMode()).thenReturn(ControlMode.READ_ONLY);
        when(invocationContext.executionId()).thenReturn(new ExecutionId("execution-test"));
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
                        new ExecutionPolicy(3, 1, timeout),
                        com.xuejiai.aaf.framework.intelligent.agent.model.ModelSelectionRequirement
                                .balanced(),
                        false);
        return new AgentExecutionCommand(
                dynamic,
                Optional.of(
                        new AgentExecutionCommand.RoleAssignment(
                                "system.role.test",
                                "测试角色",
                                List.of("执行测试"),
                                List.of("越权操作"))),
                Optional.of(executionModel),
                "技能提示",
                Set.of(),
                0,
                List.of(new AgentMessage("message-1", AgentMessage.Role.USER, "执行")),
                invocationContext);
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
                        command.effectiveSystemPromptAppendix(),
                        command.roleAllowedToolNames()))
                .thenReturn(agent);
        when(agent.streamEvents(anyList(), eq(runtimeContext))).thenReturn(events);
    }
}
