package com.xuejiai.aaf.module.ai.agui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.event.publication.ExecutionEventPublicMapper;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

import io.agentscope.core.agui.encoder.AguiEventEncoder;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.event.AguiEventType;

/**
 * AG-UI 投影契约测试。
 *
 * <p>契约的真理源是前端 {@code @ag-ui/core} 的 zod schema（{@code EventSchemas.parse} 是硬校验，
 * 失败即中断整条流）。因此这里既验配对状态机，也验序列化后的 JSON 形状。
 */
class AgUiProjectorTest {

    private static final String THREAD_ID = "thread-1";
    private static final String RUN_ID = "run-1";
    private static final String EXECUTION_ID = "execution-1";
    private static final AguiEventEncoder ENCODER = new AguiEventEncoder();

    private final AgUiProjector projector = new AgUiProjector(new ExecutionEventPublicMapper());

    @Test
    @DisplayName("RUN_STARTED 携带 threadId 与 runId，且 type 仅出现一次")
    void should_emit_run_started_with_thread_id() {
        var session = projector.openSession();

        var events = session.project(event(1, ExecutionEventType.EXECUTION_STARTED, Map.of()));

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getType()).isEqualTo(AguiEventType.RUN_STARTED);
        var json = json(events.getFirst());
        // threadId 是 @ag-ui/core RunStartedSchema 的必填字段，缺失会让标准客户端拒绝首个事件
        assertThat(json).contains("\"threadId\":\"" + THREAD_ID + "\"");
        assertThat(json).contains("\"runId\":\"" + RUN_ID + "\"");
        // @JsonTypeInfo 注入的 type 与 getType() 不得同时输出
        assertThat(countOccurrences(json, "\"type\"")).isEqualTo(1);
        assertThat(json).contains("\"type\":\"RUN_STARTED\"");
        // @JsonInclude(NON_NULL) 必须生效：zod 的 ZodOptional 不接受显式 null
        assertThat(json).doesNotContain("null");
    }

    @Test
    @DisplayName("重复的 MESSAGE_STARTED 只开启一次 TEXT_MESSAGE_START")
    void should_deduplicate_text_message_start() {
        var session = projector.openSession();

        var first = session.project(event(1, ExecutionEventType.MESSAGE_STARTED, Map.of()));
        var second = session.project(event(2, ExecutionEventType.MESSAGE_STARTED, Map.of()));

        assertThat(first).hasSize(1);
        assertThat(first.getFirst().getType()).isEqualTo(AguiEventType.TEXT_MESSAGE_START);
        assertThat(json(first.getFirst())).contains("\"role\":\"assistant\"");
        assertThat(second).isEmpty();
    }

    @Test
    @DisplayName("MESSAGE_DELTA 在未开启消息时自动补 TEXT_MESSAGE_START")
    void should_open_message_before_content() {
        var session = projector.openSession();

        var events =
                session.project(
                        event(1, ExecutionEventType.MESSAGE_DELTA, Map.of("delta", "你好")));

        assertThat(events.stream().map(AguiEvent::getType))
                .containsExactly(AguiEventType.TEXT_MESSAGE_START, AguiEventType.TEXT_MESSAGE_CONTENT);
        assertThat(json(events.get(1))).contains("\"delta\":\"你好\"");
    }

    @Test
    @DisplayName("close 兜底闭合未配对的消息并终结 run")
    void should_close_dangling_message_and_run() {
        var session = projector.openSession();
        session.project(event(1, ExecutionEventType.MESSAGE_STARTED, Map.of()));

        var closing = session.close(THREAD_ID, RUN_ID);

        assertThat(closing.stream().map(AguiEvent::getType))
                .containsExactly(AguiEventType.TEXT_MESSAGE_END, AguiEventType.RUN_FINISHED);
    }

    @Test
    @DisplayName("已正常终结的 run 不会被 close 重复终结")
    void should_not_finish_run_twice() {
        var session = projector.openSession();
        session.project(event(1, ExecutionEventType.EXECUTION_COMPLETED, Map.of()));

        assertThat(session.close(THREAD_ID, RUN_ID)).isEmpty();
    }

    @Test
    @DisplayName("叶子 RUN_FAILED 收敛为 RUN_ERROR 并闭合 run，不再降级为 CUSTOM")
    void should_map_run_failed_to_run_error() {
        var session = projector.openSession();

        var events = session.project(event(1, ExecutionEventType.RUN_FAILED, Map.of()));

        assertThat(events.stream().map(AguiEvent::getType))
                .containsExactly(AguiEventType.RUN_ERROR, AguiEventType.RUN_FINISHED);
        assertThat(json(events.getFirst())).contains("\"code\":\"AGENT_RUN_FAILED\"");
    }

    @Test
    @DisplayName("工具结果先补 TOOL_CALL_END，content 为字符串")
    void should_close_tool_call_before_result() {
        var session = projector.openSession();
        var payload = Map.<String, Object>of("toolCallId", "tc-1", "toolName", "knowledge.search");
        session.project(event(1, ExecutionEventType.TOOL_CALL_STARTED, payload));

        var events = session.project(event(2, ExecutionEventType.TOOL_CALL_COMPLETED, payload));

        assertThat(events.stream().map(AguiEvent::getType))
                .containsExactly(AguiEventType.TOOL_CALL_END, AguiEventType.TOOL_CALL_RESULT);
        var json = json(events.get(1));
        // AG-UI 的 content 契约是字符串，不能直接塞对象
        assertThat(json).contains("\"toolCallId\":\"tc-1\"");
        assertThat(json).contains("\"content\":\"");
    }

    @Test
    @DisplayName("CUSTOM 的 createdAt 输出 ISO-8601 字符串，不是数字时间戳")
    void should_render_custom_created_at_as_iso_string() {
        var session = projector.openSession();

        var events = session.project(event(1, ExecutionEventType.MODEL_CALL_STARTED, Map.of()));

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getType()).isEqualTo(AguiEventType.CUSTOM);
        var json = json(events.getFirst());
        assertThat(json).contains("\"name\":\"aaf.model.started\"");
        // 前端 isAafAiTaskEvent 要求 createdAt 是字符串；Jackson 2 的 JavaTimeModule 默认写数字
        assertThat(json).contains("\"createdAt\":\"2026-08-19T00:00:00Z\"");
    }

    private static String json(AguiEvent event) {
        return ENCODER.encodeToJson(event).trim();
    }

    private static int countOccurrences(String text, String token) {
        var count = 0;
        var index = text.indexOf(token);
        while (index >= 0) {
            count++;
            index = text.indexOf(token, index + token.length());
        }
        return count;
    }

    private static ExecutionEvent event(
            long sequence, ExecutionEventType type, Map<String, Object> payload) {
        return new ExecutionEvent(
                new EventId("event-" + sequence),
                new TenantId("tenant-1"),
                new ConversationId(THREAD_ID),
                new SessionId(THREAD_ID),
                new TaskId("task-1"),
                new ExecutionId(EXECUTION_ID),
                new RunId(RUN_ID),
                null,
                sequence,
                type,
                ExecutionEventStatus.RUNNING,
                ControlMode.COLLABORATIVE,
                OwnerType.SYSTEM,
                null,
                null,
                null,
                new CorrelationId(THREAD_ID),
                null,
                null,
                new ExecutionEventPayload(payload),
                Instant.parse("2026-08-19T00:00:00Z"));
    }
}
