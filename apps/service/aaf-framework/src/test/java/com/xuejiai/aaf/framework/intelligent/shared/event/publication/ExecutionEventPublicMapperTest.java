package com.xuejiai.aaf.framework.intelligent.shared.event.publication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AgentId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

class ExecutionEventPublicMapperTest {

    private final ExecutionEventPublicMapper mapper = new ExecutionEventPublicMapper();

    @Test
    @DisplayName("Given 精确 content 草稿预览参数事件 When 公开映射 Then 公开已解码正文与完成标记")
    void should_expose_content_draft_preview_for_exact_whitelist() {
        var delta =
                mapper.map(
                        event(
                                ExecutionEventType.TOOL_CALL_ARGS_DELTA,
                                Map.of(
                                        "toolCallId", "call-1",
                                        "toolName", "content.draft.upsert",
                                        "previewKind", "CONTENT_DRAFT",
                                        "contentDelta", "首行\n\"引号\"")));
        var completed =
                mapper.map(
                        event(
                                ExecutionEventType.TOOL_CALL_ARGS_COMPLETED,
                                Map.of(
                                        "toolCallId", "call-1",
                                        "toolName", "content.draft.upsert",
                                        "previewKind", "CONTENT_DRAFT",
                                        "contentPreviewCompleted", true)));

        assertThat(delta.data().values())
                .containsEntry("toolCallId", "call-1")
                .containsEntry("toolName", "content.draft.upsert")
                .containsEntry("previewKind", "CONTENT_DRAFT")
                .containsEntry("contentDelta", "首行\n\"引号\"");
        assertThat(completed.data().values())
                .containsEntry("previewKind", "CONTENT_DRAFT")
                .containsEntry("contentPreviewCompleted", true);
    }

    @Test
    @DisplayName("Given 非白名单或普通工具参数事件 When 公开映射 Then 不公开预览正文或完成标记")
    void should_not_expose_preview_for_non_whitelist_or_ordinary_tool_arguments() {
        var wrongPreviewKind =
                mapper.map(
                        event(
                                ExecutionEventType.TOOL_CALL_ARGS_DELTA,
                                Map.of(
                                        "toolCallId", "call-1",
                                        "toolName", "content.draft.upsert",
                                        "previewKind", "OTHER",
                                        "contentDelta", "不应公开")));
        var ordinaryTool =
                mapper.map(
                        event(
                                ExecutionEventType.TOOL_CALL_ARGS_COMPLETED,
                                Map.of(
                                        "toolCallId", "call-2",
                                        "toolName", "search",
                                        "previewKind", "CONTENT_DRAFT",
                                        "contentPreviewCompleted", true)));

        assertThat(wrongPreviewKind.data().values())
                .doesNotContainKeys(
                        "contentDelta", "contentPreviewCompleted", "previewKind", "toolName");
        assertThat(ordinaryTool.data().values())
                .doesNotContainKeys(
                        "contentDelta", "contentPreviewCompleted", "previewKind", "toolName");
    }

    private static ExecutionEvent event(ExecutionEventType type, Map<String, Object> payload) {
        return new ExecutionEvent(
                new EventId("event-1"),
                new TenantId("tenant-1"),
                new ConversationId("conversation-1"),
                new SessionId("session-1"),
                new TaskId("task-1"),
                new ExecutionId("execution-1"),
                new RunId("run-1"),
                null,
                1,
                type,
                ExecutionEventStatus.RUNNING,
                ControlMode.COLLABORATIVE,
                OwnerType.AGENT,
                null,
                new AgentId("agent-1"),
                null,
                new CorrelationId("correlation-1"),
                null,
                null,
                new ExecutionEventPayload(payload),
                Instant.parse("2026-09-20T00:00:00Z"));
    }
}
