package com.xuejiai.aaf.framework.intelligent.shared.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.NodeIdentity.NodeKind;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AgentId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

class ExecutionEventJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("Given 执行事件含派生判断方法 When Jackson2 持久化往返 Then 不写派生字段且可恢复原事件")
    void should_round_trip_without_derived_properties() throws Exception {
        // 准备参数
        var event = event();

        // 调用
        var json = mapper.writeValueAsString(event);
        var restored = mapper.readValue(json, ExecutionEvent.class);

        // 断言
        assertThat(json).doesNotContain("\"rootEvent\"", "\"terminal\"");
        assertThat(restored).isEqualTo(event);
        assertThat(restored.isRootEvent()).isTrue();
        assertThat(restored.isTerminal()).isFalse();
    }

    private static ExecutionEvent event() {
        return new ExecutionEvent(
                new EventId("event-1"),
                new TenantId("1"),
                new ConversationId("conversation-1"),
                new SessionId("session-1"),
                new TaskId("task-1"),
                new ExecutionId("execution:1"),
                new RunId("run-1"),
                null,
                1,
                ExecutionEventType.RUN_STARTED,
                ExecutionEventStatus.RUNNING,
                ControlMode.DELEGATED,
                OwnerType.AGENT,
                null,
                new AgentId("agent-1"),
                new UserId("1"),
                new CorrelationId("correlation-1"),
                null,
                null,
                new ExecutionEventPayload(Map.of("message", "started")),
                Instant.parse("2026-10-25T02:21:47Z"),
                new NodeIdentity(
                        "coordinator",
                        NodeKind.COORDINATOR,
                        "system.role.content-creator",
                        "voiceover",
                        true));
    }
}
