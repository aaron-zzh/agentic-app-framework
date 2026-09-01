package com.xuejiai.aaf.module.ai.agui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.agentscope.core.agui.event.AguiEvent;

/**
 * 单次 run 的 AG-UI 投影状态与配对不变量。
 *
 * <p>AG-UI 客户端按 START/END 配对维护消息与工具调用状态，缺 END 会让消息永久停留在"生成中"。而 AAF 的 {@code MESSAGE_STARTED} 来自
 * AgentScope {@code TEXT_BLOCK_START}（ReAct 每轮都可能触发）、{@code MESSAGE_COMPLETED} 来自 {@code
 * AGENT_RESULT}（一次执行仅一次），因此必须按 run 跟踪 started/ended 集合去重，并在流结束时兜底闭合。
 *
 * <p><b>不变量</b>：同一 messageId / toolCallId 的 START 与 END 各最多发一次；{@code RUN_FINISHED} 每 run 恰好一次； run
 * 终结后不再发任何终态事件。所有发射都必须经本类的方法，converter 不得自行 new 配对类事件——否则不变量无处保证。
 *
 * <p>非线程安全，只应被单个事件流串行消费。
 */
public final class AafAguiStreamContext {

    private final String threadId;
    private final String runId;
    private final Set<String> startedMessages = new LinkedHashSet<>();
    private final Set<String> endedMessages = new LinkedHashSet<>();
    private final Set<String> startedToolCalls = new LinkedHashSet<>();
    private final Set<String> endedToolCalls = new LinkedHashSet<>();
    private boolean runFinished;

    public AafAguiStreamContext(String threadId, String runId) {
        this.threadId = Objects.requireNonNull(threadId, "threadId 不能为空");
        this.runId = Objects.requireNonNull(runId, "runId 不能为空");
    }

    public String threadId() {
        return threadId;
    }

    public String runId() {
        return runId;
    }

    public boolean runFinished() {
        return runFinished;
    }

    /** ReAct 多轮会重复触发 TEXT_BLOCK_START，同一 messageId 只允许开启一次。 */
    public List<AguiEvent> messageStart(String messageId) {
        if (!startedMessages.add(messageId)) {
            return List.of();
        }
        return List.of(new AguiEvent.TextMessageStart(threadId, runId, messageId, "assistant"));
    }

    /** delta 必须落在已开启的消息内，因此先补 START 再发内容。 */
    public List<AguiEvent> messageContent(String messageId, String delta) {
        var events = new ArrayList<AguiEvent>(messageStart(messageId));
        events.add(new AguiEvent.TextMessageContent(threadId, runId, messageId, delta));
        return List.copyOf(events);
    }

    /** 未开启过的消息不发 END：凭空的 END 会让客户端建出一条空消息。 */
    public List<AguiEvent> messageEnd(String messageId) {
        if (!startedMessages.contains(messageId) || !endedMessages.add(messageId)) {
            return List.of();
        }
        return List.of(new AguiEvent.TextMessageEnd(threadId, runId, messageId));
    }

    public List<AguiEvent> toolCallStart(String toolCallId, String toolName) {
        if (!startedToolCalls.add(toolCallId)) {
            return List.of();
        }
        return List.of(new AguiEvent.ToolCallStart(threadId, runId, toolCallId, toolName));
    }

    /**
     * 工具结果：先补 TOOL_CALL_END 关闭参数阶段，再发 TOOL_CALL_RESULT。
     *
     * <p>结果先到而 START 未记录时补记 startedToolCalls，避免 {@link #close()} 再补一次 END。
     */
    public List<AguiEvent> toolCallResult(String toolCallId, String content) {
        var events = new ArrayList<AguiEvent>();
        startedToolCalls.add(toolCallId);
        if (endedToolCalls.add(toolCallId)) {
            events.add(new AguiEvent.ToolCallEnd(threadId, runId, toolCallId));
        }
        events.add(
                new AguiEvent.ToolCallResult(
                        threadId, runId, toolCallId, content, "tool", toolCallId));
        return List.copyOf(events);
    }

    public List<AguiEvent> runStarted() {
        return List.of(new AguiEvent.RunStarted(threadId, runId));
    }

    public List<AguiEvent> runFinishedOnce() {
        if (runFinished) {
            return List.of();
        }
        runFinished = true;
        return List.of(new AguiEvent.RunFinished(threadId, runId));
    }

    /** 失败终态：RUN_ERROR 后必须紧跟 RUN_FINISHED，AG-UI 要求 run 必须终结。 */
    public List<AguiEvent> runError(String code) {
        if (runFinished) {
            return List.of();
        }
        runFinished = true;
        return List.of(
                new AguiEvent.RunError(threadId, runId, "Assistant 运行未完成", code),
                new AguiEvent.RunFinished(threadId, runId));
    }

    /** 流结束兜底：补齐未闭合的 message 与 tool call，并确保 run 已终结。 */
    public List<AguiEvent> close() {
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
        events.addAll(runFinishedOnce());
        return List.copyOf(events);
    }
}
