package com.xuejiai.aaf.module.ai.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AgentId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.module.ai.assistant.port.ChannelAssistantExecutionPort.Request;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import reactor.core.publisher.Flux;

class DefaultChannelAssistantExecutionAdapterTest extends BaseMockitoUnitTest {

    @Mock private AssistantCommandPort assistants;

    @Test
    void should_build_stable_read_only_command_and_return_final_text() {
        var adapter = new DefaultChannelAssistantExecutionAdapter(assistants);
        var request = request();
        when(assistants.execute(org.mockito.ArgumentMatchers.any()))
                .thenReturn(
                        Flux.fromIterable(
                                List.of(
                                        event(
                                                ExecutionEventType.MESSAGE_COMPLETED,
                                                ExecutionEventStatus.RUNNING,
                                                Map.of("text", "回复内容")),
                                        event(
                                                ExecutionEventType.EXECUTION_COMPLETED,
                                                ExecutionEventStatus.COMPLETED,
                                                Map.of()))));

        var result = adapter.execute(request);

        assertThat(result).isEqualTo("回复内容");
        var captor =
                ArgumentCaptor.forClass(
                        com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand
                                .class);
        org.mockito.Mockito.verify(assistants).execute(captor.capture());
        var command = captor.getValue();
        assertThat(command.controlMode()).isEqualTo(ControlMode.READ_ONLY);
        assertThat(command.tenantId().value()).isEqualTo("10");
        assertThat(command.userId().value()).isEqualTo("20");
        assertThat(command.assistantId().value()).isEqualTo("system.assistant.default-user");
        assertThat(command.assistantVersion().value()).isEqualTo(1);
        assertThat(command.memorySubject().kind().name()).isEqualTo("VISITOR");
    }

    @Test
    void should_generate_same_execution_ids_for_duplicate_message() {
        var adapter = new DefaultChannelAssistantExecutionAdapter(assistants);
        when(assistants.execute(org.mockito.ArgumentMatchers.any()))
                .thenReturn(
                        Flux.just(
                                event(
                                        ExecutionEventType.MESSAGE_COMPLETED,
                                        ExecutionEventStatus.RUNNING,
                                        Map.of("text", "ok")),
                                event(
                                        ExecutionEventType.EXECUTION_COMPLETED,
                                        ExecutionEventStatus.COMPLETED,
                                        Map.of())));

        adapter.execute(request());
        adapter.execute(request());

        var captor =
                ArgumentCaptor.forClass(
                        com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand
                                .class);
        org.mockito.Mockito.verify(assistants, org.mockito.Mockito.times(2))
                .execute(captor.capture());
        assertThat(captor.getAllValues().get(0).executionId())
                .isEqualTo(captor.getAllValues().get(1).executionId());
        assertThat(captor.getAllValues().get(0).idempotencyKey())
                .isEqualTo(captor.getAllValues().get(1).idempotencyKey());
    }

    @Test
    void should_fail_when_execution_has_no_completed_event() {
        var adapter = new DefaultChannelAssistantExecutionAdapter(assistants);
        when(assistants.execute(org.mockito.ArgumentMatchers.any()))
                .thenReturn(
                        Flux.just(
                                event(
                                        ExecutionEventType.MESSAGE_COMPLETED,
                                        ExecutionEventStatus.RUNNING,
                                        Map.of("text", "未完成"))));

        assertThatThrownBy(() -> adapter.execute(request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未完成");
    }

    private Request request() {
        return new Request(
                10L,
                20L,
                "system.assistant.default-user",
                1,
                "feishu",
                "binding-1",
                "external-user",
                "message-1",
                "你好",
                Instant.parse("2026-07-29T12:00:00Z"));
    }

    private ExecutionEvent event(
            ExecutionEventType type, ExecutionEventStatus status, Map<String, Object> payload) {
        return new ExecutionEvent(
                new EventId(java.util.UUID.randomUUID().toString()),
                new TenantId("10"),
                new com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId("c"),
                new SessionId("s"),
                new TaskId("t"),
                new ExecutionId("e"),
                new RunId("r"),
                null,
                1,
                type,
                status,
                ControlMode.READ_ONLY,
                OwnerType.ASSISTANT,
                new AssistantId("system.assistant.default-user"),
                (AgentId) null,
                (UserId) null,
                new CorrelationId("correlation"),
                null,
                null,
                new ExecutionEventPayload(payload),
                Instant.parse("2026-07-29T12:00:00Z"));
    }
}
