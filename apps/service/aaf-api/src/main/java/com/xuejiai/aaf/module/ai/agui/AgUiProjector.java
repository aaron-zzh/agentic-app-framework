package com.xuejiai.aaf.module.ai.agui;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.publication.AafAiTaskEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.publication.ExecutionEventPublicMapper;

import io.agentscope.core.agui.event.AguiEvent;

/**
 * 内部执行事件到 AG-UI 标准事件的唯一映射。
 *
 * <p>事件模型使用官方 {@code io.agentscope.core.agui.event.AguiEvent}，不再自研——字段契约由前端 {@code @ag-ui/core} 的
 * zod schema 强制校验（{@code EventSchemas.parse}），自研模型无法保证对齐。
 *
 * <p>投影是有状态的：AG-UI 要求 {@code TEXT_MESSAGE_START/END} 与 {@code TOOL_CALL_START/END} 成对， 而 AAF 的
 * {@code MESSAGE_STARTED} 来自 AgentScope {@code TEXT_BLOCK_START}（ReAct 每轮都可能触发）， {@code
 * MESSAGE_COMPLETED} 来自 {@code AGENT_RESULT}（一次执行仅一次）。因此必须按 run 跟踪 started/ended 集合去重，并在流结束时兜底闭合。每次
 * run 通过 {@link #openSession} 取独立会话。
 */
@Component
public final class AgUiProjector {

    private final ExecutionEventPublicMapper publicMapper;

    public AgUiProjector(ExecutionEventPublicMapper publicMapper) {
        this.publicMapper = Objects.requireNonNull(publicMapper, "publicMapper 不能为空");
    }

    /** 开启一次 run 的投影会话；配对跟踪与兜底闭合依赖 per-run 状态，禁止跨 run 复用。 */
    public Session openSession() {
        return new Session(publicMapper);
    }

    /** 单次 run 的投影状态机。非线程安全，只应被单个事件流串行消费。 */
    public static final class Session {

        private final ExecutionEventPublicMapper publicMapper;
        private final Set<String> startedMessages = new LinkedHashSet<>();
        private final Set<String> endedMessages = new LinkedHashSet<>();
        private final Set<String> startedToolCalls = new LinkedHashSet<>();
        private final Set<String> endedToolCalls = new LinkedHashSet<>();
        private boolean runFinished;

        private Session(ExecutionEventPublicMapper publicMapper) {
            this.publicMapper = publicMapper;
        }

        /** 投影单个执行事件；无对应 AG-UI 语义的事件降级为 aaf.* CUSTOM。 */
        public List<AguiEvent> project(ExecutionEvent event) {
            var threadId = threadId(event);
            var runId = event.runId().value();
            return switch (event.type()) {
                case EXECUTION_STARTED -> List.of(new AguiEvent.RunStarted(threadId, runId));
                case MESSAGE_STARTED -> messageStart(threadId, runId, messageId(event));
                case MESSAGE_DELTA ->
                        messageContent(threadId, runId, messageId(event), requiredDelta(event));
                case MESSAGE_COMPLETED -> messageEnd(threadId, runId, messageId(event));
                case EXECUTION_COMPLETED -> runFinished(threadId, runId);
                // RUN_FAILED 是叶子 Agent 失败，EXECUTION_* 是任务级终态；两者都必须收敛为 RUN_ERROR，
                // 否则前端只能收到 CUSTOM，无法识别失败。
                case EXECUTION_FAILED, EXECUTION_CANCELED, COMMAND_REJECTED, RUN_FAILED ->
                        runError(threadId, runId, errorCode(event));
                default -> projectSafe(threadId, runId, publicMapper.map(event));
            };
        }

        /**
         * 流结束时兜底：补齐未闭合的 message 与 tool call，并确保 run 已终结。
         *
         * <p>AG-UI 客户端按 START/END 配对维护消息状态，缺 END 会让消息永久停留在"生成中"。
         */
        public List<AguiEvent> close(String threadId, String runId) {
            var events = new ArrayList<AguiEvent>();
            for (var messageId : startedMessages) {
                if (endedMessages.add(messageId)) {
                    events.add(new AguiEvent.TextMessageEnd(threadId, runId, messageId));
                }
            }
            for (var toolCallId : startedToolCalls) {
                if (endedToolCalls.add(toolCallId)) {
                    events.add(new AguiEvent.ToolCallEnd(threadId, runId, toolCallId));
                }
            }
            if (!runFinished) {
                runFinished = true;
                events.add(new AguiEvent.RunFinished(threadId, runId));
            }
            return events;
        }

        /** 异常路径：发 RUN_ERROR 并闭合 run；AG-UI 要求 run 必须终结。 */
        public List<AguiEvent> fail(String threadId, String runId, String code) {
            return runError(threadId, runId, code);
        }

        private List<AguiEvent> messageStart(String threadId, String runId, String messageId) {
            if (!startedMessages.add(messageId)) {
                // ReAct 多轮会重复触发 TEXT_BLOCK_START，同一 messageId 只允许开启一次
                return List.of();
            }
            return List.of(new AguiEvent.TextMessageStart(threadId, runId, messageId, "assistant"));
        }

        private List<AguiEvent> messageContent(
                String threadId, String runId, String messageId, String delta) {
            var events = new ArrayList<AguiEvent>(messageStart(threadId, runId, messageId));
            events.add(new AguiEvent.TextMessageContent(threadId, runId, messageId, delta));
            return List.copyOf(events);
        }

        private List<AguiEvent> messageEnd(String threadId, String runId, String messageId) {
            if (!startedMessages.contains(messageId) || !endedMessages.add(messageId)) {
                return List.of();
            }
            return List.of(new AguiEvent.TextMessageEnd(threadId, runId, messageId));
        }

        private List<AguiEvent> runFinished(String threadId, String runId) {
            if (runFinished) {
                return List.of();
            }
            runFinished = true;
            return List.of(new AguiEvent.RunFinished(threadId, runId));
        }

        private List<AguiEvent> runError(String threadId, String runId, String code) {
            if (runFinished) {
                return List.of();
            }
            runFinished = true;
            return List.of(
                    new AguiEvent.RunError(threadId, runId, "Assistant 运行未完成", code),
                    new AguiEvent.RunFinished(threadId, runId));
        }

        /** 工具事件走 AG-UI 标准三段式，其余安全事件降级为 CUSTOM。 */
        private List<AguiEvent> projectSafe(
                String threadId, String runId, AafAiTaskEvent publicEvent) {
            var data = publicEvent.data().values();
            var toolCallId = text(data, "toolCallId");
            return switch (publicEvent.type()) {
                case "aaf.tool.started" -> {
                    var toolName = text(data, "toolName");
                    yield toolCallId == null || toolName == null
                            ? custom(threadId, runId, publicEvent)
                            : toolCallStart(threadId, runId, toolCallId, toolName);
                }
                case "aaf.tool.completed", "aaf.tool.failed" ->
                        toolCallId == null
                                ? custom(threadId, runId, publicEvent)
                                : toolCallResult(threadId, runId, toolCallId, data);
                default -> custom(threadId, runId, publicEvent);
            };
        }

        private List<AguiEvent> toolCallStart(
                String threadId, String runId, String toolCallId, String toolName) {
            if (!startedToolCalls.add(toolCallId)) {
                return List.of();
            }
            return List.of(new AguiEvent.ToolCallStart(threadId, runId, toolCallId, toolName));
        }

        /**
         * 工具结果：先补 TOOL_CALL_END 关闭参数阶段，再发 TOOL_CALL_RESULT。
         *
         * <p>AG-UI 的 {@code content} 契约是字符串，因此把脱敏后的安全字段序列化为 JSON 文本；结构化消费 仍可解析该字符串。
         */
        private List<AguiEvent> toolCallResult(
                String threadId, String runId, String toolCallId, Map<String, Object> data) {
            var events = new ArrayList<AguiEvent>();
            startedToolCalls.add(toolCallId);
            if (endedToolCalls.add(toolCallId)) {
                events.add(new AguiEvent.ToolCallEnd(threadId, runId, toolCallId));
            }
            events.add(
                    new AguiEvent.ToolCallResult(
                            threadId,
                            runId,
                            toolCallId,
                            JsonUtils.toJsonString(data),
                            "tool",
                            toolCallId));
            return List.copyOf(events);
        }

        private static List<AguiEvent> custom(
                String threadId, String runId, AafAiTaskEvent publicEvent) {
            return List.of(
                    new AguiEvent.Custom(
                            threadId, runId, publicEvent.type(), customValue(publicEvent)));
        }

        /**
         * CUSTOM 载荷显式建 Map，不直接传 record。
         *
         * <p>{@code AguiEventEncoder} 用 agentscope-core 的 Jackson 2 codec 序列化，其 JavaTimeModule 默认把
         * {@code Instant} 写成数字时间戳；前端 {@code isAafAiTaskEvent} 要求 {@code createdAt} 是字符串。这里固定输出
         * ISO-8601，避免依赖上游日期策略。
         */
        private static Map<String, Object> customValue(AafAiTaskEvent publicEvent) {
            var value = new LinkedHashMap<String, Object>();
            value.put("eventId", publicEvent.eventId());
            value.put("taskId", publicEvent.taskId());
            value.put("executionId", publicEvent.executionId());
            value.put("runId", publicEvent.runId());
            value.put("sessionId", publicEvent.sessionId());
            value.put("sequence", publicEvent.sequence());
            value.put("eventOffset", publicEvent.eventOffset());
            value.put("status", publicEvent.status());
            value.put("type", publicEvent.type());
            value.put("version", publicEvent.version());
            value.put("audience", publicEvent.audience().name());
            value.put("delivery", publicEvent.delivery().name());
            value.put("data", publicEvent.data().values());
            value.put("createdAt", isoInstant(publicEvent.createdAt()));
            return value;
        }

        private static String isoInstant(Instant instant) {
            return instant == null ? Instant.EPOCH.toString() : instant.toString();
        }

        /**
         * AG-UI 的 threadId 对应 AAF 的 conversationId。
         *
         * <p>{@code AssistantExecutionService.RunIdentity.create} 用同一个 threadId 构造 ConversationId /
         * SessionId，且 {@code ExecutionEvent} 对 conversationId 有非空约束， 因此这里必然拿到调用方传入的 threadId。
         */
        private static String threadId(ExecutionEvent event) {
            return event.conversationId().value();
        }

        /**
         * 当前用 executionId 作为 messageId。
         *
         * <p>AgentScope 的 replyId/blockId 尚未在 {@code AgentScopeEventMapper} 的 TEXT_BLOCK_START 分支进入
         * payload，因此无法按 block 分消息。配对跟踪已消除重复 START，语义正确性待补 replyId。
         */
        private static String messageId(ExecutionEvent event) {
            return event.executionId().value();
        }

        private static String errorCode(ExecutionEvent event) {
            return switch (event.type()) {
                case EXECUTION_CANCELED -> "RUN_CANCELED";
                case COMMAND_REJECTED -> "ASSISTANT_COMMAND_REJECTED";
                case RUN_FAILED -> "AGENT_RUN_FAILED";
                default -> "ASSISTANT_EXECUTION_FAILED";
            };
        }

        private static String requiredDelta(ExecutionEvent event) {
            var delta = event.payload().values().get("delta");
            if (delta instanceof String text) {
                return text;
            }
            throw new IllegalStateException("MESSAGE_DELTA 缺少字符串 delta");
        }

        private static String text(Map<String, Object> values, String key) {
            var value = values.get(key);
            return value instanceof String text ? text : null;
        }
    }
}
