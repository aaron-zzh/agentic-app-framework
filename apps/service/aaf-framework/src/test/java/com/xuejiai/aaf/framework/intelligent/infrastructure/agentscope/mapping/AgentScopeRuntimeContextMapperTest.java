package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort.Lease;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CausationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.IdempotencyKey;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** AgentScope 状态槽键必须隔离 Agent 身份，并以稳定 stateSlot 标识 attempt。 */
class AgentScopeRuntimeContextMapperTest {

    private final AgentScopeRuntimeContextMapper mapper = new AgentScopeRuntimeContextMapper();

    @Test
    @DisplayName("Given 同一租户会话 When 换 Agent 身份 Then 状态键不同")
    void should_isolate_state_slot_by_agent_identity() {
        var context = context("execution-1");

        var first = mapper.stateUserKey(context, "agent.alpha");
        var second = mapper.stateUserKey(context, "agent.beta");

        assertThat(first).isNotEqualTo(second);
        assertThat(first).contains("agent=agent.alpha").contains("stateSlot=execution-1");
    }

    @Test
    @DisplayName("Given 同一 Agent 与会话 When 换 executionId Then 状态键不同")
    void should_isolate_state_slot_by_execution() {
        var first = mapper.stateUserKey(context("execution-1"), "agent.alpha");
        var second = mapper.stateUserKey(context("execution-2"), "agent.alpha");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("Given RuntimeContext 映射 When 生成 userId Then 与状态键同值")
    void should_use_state_key_as_runtime_user_id() {
        var context = context("execution-1");

        var runtimeContext = mapper.toAgentScope(context, "agent.alpha");

        assertThat(runtimeContext.getUserId())
                .isEqualTo(mapper.stateUserKey(context, "agent.alpha"));
    }

    @Test
    @DisplayName("Given 空 agentIdentifier When 生成状态键 Then 拒绝")
    void should_reject_blank_agent_identifier() {
        assertThatThrownBy(() -> mapper.stateUserKey(context("execution-1"), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static InvocationContext context(String executionId) {
        return new InvocationContext(
                new TenantId("tenant-1"),
                new UserId("user-1"),
                null,
                new AssistantId("assistant-1"),
                new ConversationId("conversation-1"),
                new SessionId("session-1"),
                new TaskId("task-1"),
                new ExecutionId(executionId),
                new RunId("run-1"),
                null,
                new CorrelationId("correlation-1"),
                new CausationId("causation-1"),
                new IdempotencyKey("idempotency-1"),
                ControlMode.READ_ONLY,
                null,
                (Lease) null,
                new ToolAuthorizationContext(java.util.Map.of()));
    }
}
