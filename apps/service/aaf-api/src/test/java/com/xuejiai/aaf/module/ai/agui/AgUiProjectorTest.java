package com.xuejiai.aaf.module.ai.agui;

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
import com.xuejiai.aaf.framework.intelligent.shared.event.NodeIdentity;
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
        var payload = Map.<String, Object>of("replyId", "reply-1", "blockId", "block-1");

        var first = session.project(event(1, ExecutionEventType.MESSAGE_STARTED, payload));
        var second = session.project(event(2, ExecutionEventType.MESSAGE_STARTED, payload));

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
                        event(
                                1,
                                ExecutionEventType.MESSAGE_DELTA,
                                Map.of("replyId", "reply-1", "blockId", "block-1", "delta", "你好")));

        assertThat(events.stream().map(AguiEvent::getType))
                .containsExactly(
                        AguiEventType.TEXT_MESSAGE_START, AguiEventType.TEXT_MESSAGE_CONTENT);
        assertThat(json(events.get(1))).contains("\"delta\":\"你好\"");
    }

    @Test
    @DisplayName("MESSAGE_BLOCK_COMPLETED 闭合对应文本块，不同 blockId 各自独立配对")
    void should_close_message_block_by_block_id() {
        var session = projector.openSession();
        var payload = Map.<String, Object>of("replyId", "reply-1", "blockId", "block-1");
        session.project(event(1, ExecutionEventType.MESSAGE_STARTED, payload));

        var events = session.project(event(2, ExecutionEventType.MESSAGE_BLOCK_COMPLETED, payload));

        assertThat(events.stream().map(AguiEvent::getType))
                .containsExactly(AguiEventType.TEXT_MESSAGE_END);
        assertThat(json(events.getFirst())).contains("\"messageId\":\"reply-1:block-1\"");
    }

    @Test
    @DisplayName("MESSAGE_COMPLETED 不重复闭合块级消息，交给 close 兜底")
    void should_not_close_block_on_message_completed() {
        var session = projector.openSession();
        session.project(
                event(
                        1,
                        ExecutionEventType.MESSAGE_STARTED,
                        Map.of("replyId", "reply-1", "blockId", "block-1")));

        var events = session.project(event(2, ExecutionEventType.MESSAGE_COMPLETED, Map.of()));

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("close 兜底闭合未配对的消息并终结 run")
    void should_close_dangling_message_and_run() {
        var session = projector.openSession();
        session.project(
                event(
                        1,
                        ExecutionEventType.MESSAGE_STARTED,
                        Map.of("replyId", "reply-1", "blockId", "block-1")));

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

    @Test
    @DisplayName("EXECUTOR_PLAN_CREATED/SUBMITTED 映射为 planning 阶段成对事件")
    void should_map_executor_plan_events_to_planning_step() {
        var session = projector.openSession();

        var started = session.project(event(1, ExecutionEventType.EXECUTOR_PLAN_CREATED, Map.of()));
        var finished =
                session.project(event(2, ExecutionEventType.EXECUTOR_PLAN_SUBMITTED, Map.of()));

        assertThat(started.stream().map(AguiEvent::getType))
                .containsExactly(AguiEventType.STEP_STARTED);
        assertThat(json(started.getFirst())).contains("\"stepName\":\"planning\"");
        assertThat(finished.stream().map(AguiEvent::getType))
                .containsExactly(AguiEventType.STEP_STARTED);
    }

    @Test
    @DisplayName(
            "内部节点 EXECUTION_STARTED/COMPLETED 追加 State/Activity 快照增量与 execution 阶段事件，且不替代 CUSTOM 投影")
    void should_append_execution_step_alongside_custom_for_internal_node() {
        var session = projector.openSession();
        var node = new NodeIdentity("sub-1", NodeIdentity.NodeKind.EXECUTOR, "role-1", null, false);

        var started = session.project(stepEvent(1, ExecutionEventType.EXECUTION_STARTED, node));
        var finished = session.project(stepEvent(2, ExecutionEventType.EXECUTION_COMPLETED, node));

        // 附加产出顺序：State（全局看板）→ Activity（对话时间线卡片）→ Step（阶段通知）→ CUSTOM（兜底），
        // 首次子任务状态变化各发一次 Snapshot，随后同一 subTaskId 变化各发一次 Delta；四者互不替代
        assertThat(started.stream().map(AguiEvent::getType))
                .containsExactly(
                        AguiEventType.STATE_SNAPSHOT,
                        AguiEventType.ACTIVITY_SNAPSHOT,
                        AguiEventType.STEP_STARTED,
                        AguiEventType.CUSTOM);
        assertThat(json(started.get(2))).contains("\"stepName\":\"execution\"");
        assertThat(json(started.getFirst())).contains("\"subTaskId\":\"sub-1\"");
        assertThat(json(started.get(1))).contains("\"activityType\":\"SUBTASK\"");
        assertThat(json(started.get(1))).contains("\"messageId\":\"sub-1\"");
        assertThat(finished.stream().map(AguiEvent::getType))
                .containsExactly(
                        AguiEventType.STATE_DELTA,
                        AguiEventType.ACTIVITY_DELTA,
                        AguiEventType.STEP_FINISHED,
                        AguiEventType.CUSTOM);
        assertThat(json(finished.get(2))).contains("\"stepName\":\"execution\"");
    }

    @Test
    @DisplayName("同一子任务重复终态不产生多余 StateDelta/ActivityDelta：无变化时不发状态事件")
    void should_not_emit_state_delta_when_status_unchanged() {
        var session = projector.openSession();
        var node = new NodeIdentity("sub-1", NodeIdentity.NodeKind.EXECUTOR, "role-1", null, false);
        session.project(stepEvent(1, ExecutionEventType.EXECUTION_STARTED, node));

        // 同一节点、同一事件类型重复到达（如客户端重试导致的重复投递）：status 不变，不应产生噪声 StateDelta/ActivityDelta
        var repeated = session.project(stepEvent(2, ExecutionEventType.EXECUTION_STARTED, node));

        assertThat(repeated.stream().map(AguiEvent::getType))
                .doesNotContain(
                        AguiEventType.STATE_SNAPSHOT,
                        AguiEventType.STATE_DELTA,
                        AguiEventType.ACTIVITY_SNAPSHOT,
                        AguiEventType.ACTIVITY_DELTA);
    }

    @Test
    @DisplayName("根节点（交付者）EXECUTION_STARTED/COMPLETED 走 run 生命周期，不产生 Step 事件")
    void should_not_emit_step_for_delivery_node_execution_events() {
        var session = projector.openSession();
        // AGGREGATOR_REDUCE 契约下 AGGREGATOR 是唯一交付者：delivery=true 时按根节点路径处理，
        // 走 RunLifecycleEventConverter 的 run 生命周期语义（RUN_STARTED/RUN_FINISHED），不产生 Step 事件。
        var events = session.project(event(1, ExecutionEventType.EXECUTION_STARTED, Map.of()));

        assertThat(events.stream().map(AguiEvent::getType))
                .containsExactly(AguiEventType.RUN_STARTED);
    }

    @Test
    @DisplayName("VALIDATION_STARTED/COMPLETED 映射为 verification 阶段，不受内部节点降级影响")
    void should_map_validation_events_to_verification_step_even_for_internal_node() {
        var session = projector.openSession();
        var node = new NodeIdentity("sub-1", NodeIdentity.NodeKind.EXECUTOR, "role-1", null, false);

        var started = session.project(stepEvent(1, ExecutionEventType.VALIDATION_STARTED, node));
        var finished = session.project(stepEvent(2, ExecutionEventType.VALIDATION_COMPLETED, node));

        assertThat(started.getFirst().getType()).isEqualTo(AguiEventType.STEP_STARTED);
        assertThat(json(started.getFirst())).contains("\"stepName\":\"verification\"");
        assertThat(finished.getFirst().getType()).isEqualTo(AguiEventType.STEP_FINISHED);
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

    /**
     * 带 {@code nodeIdentity} 的事件构造，用于验证 Step/State 事件按节点类型与事件语义区分。
     *
     * <p>{@code status} 按 {@code type} 推导而非恒定 {@code RUNNING}——{@code EXECUTION_COMPLETED} 等终态
     * 类型若仍标 {@code RUNNING} 会让 {@code StateSnapshot}/{@code StateDelta} 的测试断言失真（真实事件的 {@code
     * status} 与 {@code type} 语义一致，如 {@code AssistantApplicationService.status(TaskStatus)}）。
     */
    private static ExecutionEvent stepEvent(
            long sequence, ExecutionEventType type, NodeIdentity nodeIdentity) {
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
                statusFor(type),
                ControlMode.COLLABORATIVE,
                OwnerType.SYSTEM,
                null,
                null,
                null,
                new CorrelationId(THREAD_ID),
                null,
                null,
                new ExecutionEventPayload(Map.of()),
                Instant.parse("2026-08-19T00:00:00Z"),
                nodeIdentity);
    }

    private static ExecutionEventStatus statusFor(ExecutionEventType type) {
        return switch (type) {
            case EXECUTION_COMPLETED, VALIDATION_COMPLETED -> ExecutionEventStatus.COMPLETED;
            case EXECUTION_FAILED, VALIDATION_FAILED -> ExecutionEventStatus.FAILED;
            case EXECUTION_CANCELED -> ExecutionEventStatus.CANCELED;
            default -> ExecutionEventStatus.RUNNING;
        };
    }
}
