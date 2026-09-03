package com.xuejiai.aaf.module.ai.agui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.agentscope.core.agui.converter.AguiStateConverter;
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
    private final Map<String, Map<String, Object>> subTaskStates = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> lastPublishedActivityContent =
            new LinkedHashMap<>();
    private final AguiStateConverter stateConverter = new AguiStateConverter();
    private Map<String, Object> lastPublishedSnapshot;
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

    /** 阶段边界不做去重：同一 stepName 在同一 run 内可能因并行子任务多次开始/结束，忠实转发每一次。 */
    public List<AguiEvent> stepStarted(String stepName) {
        return List.of(new AguiEvent.StepStarted(threadId, runId, stepName));
    }

    public List<AguiEvent> stepFinished(String stepName) {
        return List.of(new AguiEvent.StepFinished(threadId, runId, stepName));
    }

    /**
     * 子任务状态变化投影为 {@code StateSnapshot}/{@code StateDelta}（AAF-104 #10403）。
     *
     * <p><b>状态完全从事件流自身重建，不反查 {@code TaskBoardPort}</b>：与 {@code startedMessages}/{@code
     * startedToolCalls} 同一模式——每个子任务的开始/终态事件自带 {@code nodeIdentity}（{@code subTaskId}/{@code
     * kind}/{@code roleKey}）与 {@code status}，足够在内存里增量维护一份"目前已知的子任务状态表"，不需要为了拿一份
     * 完整快照就引入领域仓储依赖，保持投影层"纯粹消费事件流"的既有定位。
     *
     * <p>首次调用（{@code lastPublishedSnapshot == null}）发 {@code StateSnapshot}（全量）；此后用官方 {@link
     * AguiStateConverter#createDelta} 对比上一次已发布的快照算 RFC 6902 JSON Patch，发 {@code StateDelta}
     * （无变化时返回空列表，不产生噪声事件）。
     *
     * <p>{@code state} 形状对齐 assistant-ui {@code useAgUiState} 的"任意自定义 JSON 对象"约定，字段只含安全的
     * 展示级信息（{@code subTaskId}/{@code kind}/{@code roleKey}/{@code status}），不包含目标文本、
     * {@code forwardedProps}、凭据或完整 TaskBoard payload。
     */
    public List<AguiEvent> subTaskStateChanged(
            String subTaskId, String kind, String roleKey, String status) {
        var view = new LinkedHashMap<String, Object>();
        view.put("subTaskId", subTaskId);
        view.put("kind", kind);
        view.put("roleKey", roleKey == null ? "" : roleKey);
        view.put("status", status);
        subTaskStates.put(subTaskId, view);
        var snapshot = currentSnapshot();
        if (lastPublishedSnapshot == null) {
            lastPublishedSnapshot = snapshot;
            return List.of(stateConverter.createSnapshot(snapshot, threadId, runId));
        }
        var delta = stateConverter.createDelta(lastPublishedSnapshot, snapshot, threadId, runId);
        lastPublishedSnapshot = snapshot;
        return delta == null ? List.of() : List.of(delta);
    }

    private Map<String, Object> currentSnapshot() {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("subTasks", List.copyOf(subTaskStates.values()));
        return snapshot;
    }

    /**
     * 子任务活动卡片投影为 {@code ActivitySnapshot}/{@code ActivityDelta}（AAF-104 #10403）。
     *
     * <p>与 {@link #subTaskStateChanged} 语义互补但 UI 位置不同——State 是全局侧边栏式共享文档（{@code
     * useAgUiState}），Activity 是嵌入对话消息时间线本身的独立卡片（占用与文本消息平级但互不冲突的 {@code
     * messageId} 空间），让用户在聊天记录里直接看到"协调者拆分任务、多个执行者并行工作"这个过程。
     *
     * <p>{@code messageId} 用 {@code subTaskId} 本身——每个子任务节点对应一条稳定的活动卡片，不随事件重建；
     * {@code activityType} 固定为 {@code "SUBTASK"}，客户端按此区分 AAF 的子任务卡片与其它来源的 Activity
     * （如官方 A2UI 生成式 UI 用的 {@code "a2ui-surface"}）。
     *
     * <p><b>已知前端缺口（记入独立任务，非本次范围）</b>：核实 assistant-ui 官方文档确认 {@code useAgUiRuntime}
     * 目前只原生渲染 {@code activityType="a2ui-surface"}，其它 {@code activityType}（包括本类使用的
     * {@code "SUBTASK"}）会被静默忽略，不产生任何 UI——后端仍按协议标准实现，等前端补充自定义 {@code
     * onActivitySnapshotEvent} 渲染逻辑后即可直接生效，不需要再改后端投影。
     *
     * <p>首次调用该 {@code subTaskId} 发 {@code ActivitySnapshot}（全量）；此后同一 {@code subTaskId} 用
     * {@link AguiStateConverter#createDelta} 对比该子任务上一次已发布的 content 算增量，发 {@code
     * ActivityDelta}（无变化不发）。
     */
    public List<AguiEvent> subTaskActivityChanged(
            String subTaskId, String kind, String roleKey, String status) {
        var content = new LinkedHashMap<String, Object>();
        content.put("kind", kind);
        content.put("roleKey", roleKey == null ? "" : roleKey);
        content.put("status", status);
        var previous = lastPublishedActivityContent.get(subTaskId);
        lastPublishedActivityContent.put(subTaskId, content);
        if (previous == null) {
            return List.of(
                    new AguiEvent.ActivitySnapshot(threadId, runId, subTaskId, "SUBTASK", content));
        }
        var delta = stateConverter.createDelta(previous, content, threadId, runId);
        if (delta == null) {
            return List.of();
        }
        return List.of(
                new AguiEvent.ActivityDelta(threadId, runId, subTaskId, "SUBTASK", delta.delta()));
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
