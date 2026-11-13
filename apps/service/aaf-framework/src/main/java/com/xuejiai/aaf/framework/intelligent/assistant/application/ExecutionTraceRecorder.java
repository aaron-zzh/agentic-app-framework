package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import com.xuejiai.aaf.framework.intelligent.agent.port.ToolGatewayPort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;

import lombok.extern.slf4j.Slf4j;

/**
 * 单次助理执行（DIRECT、Task、TaskNode）的轨迹摘要收集器。
 *
 * <p>从执行事件流与少量编排期局部信息聚合角色、技能、工具、L1 上下文、模型与工具调用、拆分、验证与执行后学习， 执行结束时以 INFO
 * 打印一个多行日志块。只记录名称、数量、耗时、长度与状态；Prompt、工具参数、工具结果与记忆正文 一律不进入摘要。
 */
@Slf4j
final class ExecutionTraceRecorder {

    private static final String NONE = "—";
    private static final int MAX_TOOL_NAMES = 20;
    private static final int MAX_MESSAGE_LENGTH = 160;

    private final boolean enabled;
    private final String executionId;
    private final String mode;
    private final String taskId;
    private final String nodeKey;
    private final String nodeKind;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicBoolean finished = new AtomicBoolean();

    private String agentKind;
    private String roleKey;
    private String roleName;
    private List<String> skillCodes;
    private String requestedSkill;
    private List<String> toolNames;
    private String modelId;
    private Integer memoryCount;
    private Integer knowledgeCount;
    private Integer materialCount;
    private Integer promptTokens;
    private int contextWindow;
    private final List<ModelCall> modelCalls = new ArrayList<>();
    private final Map<String, ToolCall> toolCalls = new LinkedHashMap<>();
    private String promotedTaskId;
    private List<ChildNode> children;
    private String validation;
    private String sessionTurn;
    private String longTermMemory;
    private String result;
    private String endReason;
    private final List<String> failures = new ArrayList<>();

    ExecutionTraceRecorder(
            boolean enabled,
            String executionId,
            String mode,
            String taskId,
            String nodeKey,
            String nodeKind) {
        this.enabled = enabled;
        this.executionId = executionId;
        this.mode = mode;
        this.taskId = taskId;
        this.nodeKey = nodeKey;
        this.nodeKind = nodeKind;
    }

    /** 按命令推导摘要头：无 taskId 为 DIRECT，带节点身份为 NODE，其余为 TASK。 */
    static ExecutionTraceRecorder start(boolean enabled, AssistantCommand command) {
        var node = command.nodeIdentity();
        var mode = command.taskId() == null ? "DIRECT" : node != null ? "NODE" : "TASK";
        return new ExecutionTraceRecorder(
                enabled,
                command.executionId().value(),
                mode,
                command.taskId() == null ? null : command.taskId().value(),
                node == null ? null : node.nodeId(),
                node == null ? null : node.kind().name());
    }

    boolean enabled() {
        return enabled;
    }

    void profile(
            String agentKind,
            String roleKey,
            String roleName,
            List<String> skillCodes,
            String requestedSkill,
            List<String> toolNames,
            String modelId) {
        guarded(
                () -> {
                    this.agentKind = agentKind;
                    this.roleKey = roleKey;
                    this.roleName = roleName;
                    this.skillCodes = List.copyOf(skillCodes);
                    this.requestedSkill = requestedSkill;
                    this.toolNames = List.copyOf(toolNames);
                    this.modelId = modelId;
                });
    }

    void context(
            int memoryCount,
            int knowledgeCount,
            int materialCount,
            Integer promptTokens,
            int contextWindow) {
        guarded(
                () -> {
                    this.memoryCount = memoryCount;
                    this.knowledgeCount = knowledgeCount;
                    this.materialCount = materialCount;
                    this.promptTokens = promptTokens;
                    this.contextWindow = contextWindow;
                });
    }

    /** 观察一条执行事件；只读取事件类型、时间与白名单载荷字段。 */
    void observe(ExecutionEvent event) {
        guarded(() -> accept(event));
    }

    void validation(String outcome, String reason) {
        guarded(() -> this.validation = reason == null ? outcome : outcome + " " + reason);
    }

    void plan(List<ChildNode> children) {
        guarded(() -> this.children = List.copyOf(children));
    }

    void sessionTurn(String status) {
        guarded(() -> this.sessionTurn = status);
    }

    void longTermMemorySkipped() {
        guarded(() -> this.longTermMemory = "未启用");
    }

    void longTermMemoryLearned(int stored, int rejected) {
        guarded(() -> this.longTermMemory = "写入 %d 条，拒绝 %d 条".formatted(stored, rejected));
    }

    void longTermMemoryFailed(Throwable failure) {
        guarded(() -> this.longTermMemory = "失败：" + describe(failure));
    }

    void failure(Throwable failure) {
        guarded(() -> failures.add(describe(failure)));
    }

    /** 执行结束时打印摘要；同一执行只打印一次。 */
    void finish(String signal, long durationMillis) {
        if (!enabled || !finished.compareAndSet(false, true)) {
            return;
        }
        log.info(render(signal, durationMillis));
    }

    String render(String signal, long durationMillis) {
        lock.lock();
        try {
            var lines = new ArrayList<String>();
            lines.add("角色    " + roleLine());
            lines.add("技能    " + skillLine());
            lines.add("工具    " + toolLine());
            lines.add("上下文  " + contextLine());
            lines.addAll(blockLines("模型    ", modelLines()));
            lines.addAll(blockLines("工具调用 ", toolCallLines()));
            lines.addAll(blockLines("拆分    ", splitLines()));
            lines.add("验证    " + orNone(validation));
            lines.add("学习    " + learningLine());
            if (!failures.isEmpty()) {
                lines.add("失败    " + String.join("；", failures));
            }
            var text = new StringBuilder(header(signal, durationMillis));
            for (var index = 0; index < lines.size(); index++) {
                var line = lines.get(index);
                // 续行（│ 开头）总有后继兄弟行，最后一行恒为分组标签行
                var prefix =
                        line.startsWith("│") ? "  " : index == lines.size() - 1 ? "  └ " : "  ├ ";
                text.append('\n').append(prefix).append(line);
            }
            return text.toString();
        } finally {
            lock.unlock();
        }
    }

    private String header(String signal, long durationMillis) {
        var text = new StringBuilder("[执行轨迹] executionId=").append(executionId);
        text.append(" 模式=").append(mode);
        if (taskId != null) {
            text.append(" taskId=").append(taskId);
        }
        if (nodeKey != null) {
            text.append(" node=").append(nodeKey).append('（').append(nodeKind).append('）');
        }
        text.append(" 结果=").append(resultText(signal));
        text.append(" 耗时=").append(seconds(durationMillis));
        return text.toString();
    }

    private String resultText(String signal) {
        if (result != null) {
            return endReason == null ? result : result + "（" + endReason + "）";
        }
        if (!failures.isEmpty()) {
            return "FAILED";
        }
        if ("CANCEL".equals(signal)) {
            return "CANCELED（订阅取消）";
        }
        return NONE + "（signal=" + signal + "）";
    }

    private String roleLine() {
        if (roleKey == null) {
            return NONE;
        }
        var detail = new ArrayList<String>();
        if (roleName != null && !roleName.isBlank()) {
            detail.add(roleName);
        }
        if (agentKind != null) {
            detail.add(agentKind);
        }
        return detail.isEmpty() ? roleKey : roleKey + "（" + String.join("，", detail) + "）";
    }

    private String skillLine() {
        if (skillCodes == null) {
            return NONE;
        }
        var requested = requestedSkill == null ? "" : "，请求=" + requestedSkill;
        return skillCodes.isEmpty() ? "未命中" + requested : String.join(", ", skillCodes) + requested;
    }

    private String toolLine() {
        if (toolNames == null) {
            return NONE;
        }
        if (toolNames.isEmpty()) {
            return "0 个";
        }
        var shown = toolNames.stream().limit(MAX_TOOL_NAMES).toList();
        var more = toolNames.size() > shown.size() ? " ..." : "";
        return toolNames.size() + " 个：" + String.join(", ", shown) + more;
    }

    private String contextLine() {
        if (memoryCount == null) {
            return NONE;
        }
        var prompt =
                promptTokens == null
                        ? "Prompt " + NONE
                        : contextWindow > 0
                                ? "Prompt %d tokens（%d%%）"
                                        .formatted(
                                                promptTokens,
                                                Math.round(promptTokens * 100.0 / contextWindow))
                                : "Prompt %d tokens".formatted(promptTokens);
        return "记忆 %d 条，知识库 %d 条，任务资料 %d 条，%s"
                .formatted(memoryCount, knowledgeCount, materialCount, prompt);
    }

    private List<String> modelLines() {
        if (modelCalls.isEmpty()) {
            return List.of("无");
        }
        var lines = new ArrayList<String>();
        for (var index = 0; index < modelCalls.size(); index++) {
            lines.add("#" + (index + 1) + " " + modelCalls.get(index).describe(modelId));
        }
        return lines;
    }

    private List<String> toolCallLines() {
        if (toolCalls.isEmpty()) {
            return List.of("无");
        }
        var lines = new ArrayList<String>();
        var index = 0;
        for (var call : toolCalls.values()) {
            lines.add("#" + ++index + " " + call.describe());
        }
        return lines;
    }

    private List<String> splitLines() {
        if (promotedTaskId != null) {
            return List.of("已提升为任务 taskId=" + promotedTaskId);
        }
        if (children != null) {
            if (children.isEmpty()) {
                return List.of("无子节点");
            }
            var lines = new ArrayList<String>();
            lines.add(children.size() + " 个子节点");
            children.forEach(child -> lines.add(child.describe()));
            return lines;
        }
        return List.of("DIRECT".equals(mode) ? "无（未提升为任务）" : NONE);
    }

    private String learningLine() {
        return "短期会话 " + orNone(sessionTurn) + "；长期记忆 " + orNone(longTermMemory);
    }

    /** 首行跟在标签后；多于一行时标签行只放首项，其余以竖线续行缩进。 */
    private static List<String> blockLines(String label, List<String> items) {
        if (items.size() == 1) {
            return List.of(label + items.getFirst());
        }
        var lines = new ArrayList<String>();
        lines.add(label + items.getFirst());
        items.stream().skip(1).forEach(item -> lines.add("│           " + item));
        return lines;
    }

    private void accept(ExecutionEvent event) {
        var values = event.payload().values();
        switch (event.type()) {
            case MODEL_CALL_STARTED -> modelCalls.add(new ModelCall(event.createdAt()));
            case MESSAGE_STARTED,
                    MESSAGE_DELTA,
                    REASONING_STARTED,
                    REASONING_DELTA,
                    TOOL_CALL_STARTED,
                    TOOL_CALL_ARGS_DELTA ->
                    openModelCall().ifPresent(call -> call.firstOutput(event.createdAt()));
            case MODEL_CALL_COMPLETED, MODEL_CALL_FAILED -> {
                var call = openModelCall().orElse(null);
                if (call == null) {
                    call = new ModelCall(null);
                    modelCalls.add(call);
                }
                call.complete(
                        event.createdAt(),
                        event.type() == ExecutionEventType.MODEL_CALL_FAILED,
                        values);
            }
            case RUN_FAILED ->
                    failures.add(
                            "RUN_FAILED 类别=%s errorType=%s failureId=%s"
                                    .formatted(
                                            text(values.get("failureCategory")),
                                            text(values.get("errorType")),
                                            text(values.get("failureId"))));
            case EXECUTION_COMPLETED -> terminal("COMPLETED", values);
            case EXECUTION_FAILED -> terminal("FAILED", values);
            case EXECUTION_CANCELED -> terminal("CANCELED", values);
            case EXECUTION_PAUSED -> terminal("PAUSED", values);
            case COMMAND_REJECTED -> terminal("REJECTED", values);
            case EXECUTION_PROMOTED -> {
                terminal("PROMOTED", values);
                promotedTaskId = text(values.get("promotedTaskId"));
            }
            default -> {
                // 其余事件不参与摘要。
            }
        }
        acceptToolEvent(event, values);
    }

    private void acceptToolEvent(ExecutionEvent event, Map<String, Object> values) {
        var toolCallId = values.get("toolCallId");
        if (toolCallId == null) {
            return;
        }
        var key = toolCallId.toString();
        switch (event.type()) {
            case TOOL_CALL_STARTED ->
                    toolCalls.putIfAbsent(
                            key, new ToolCall(text(values.get("toolName")), event.createdAt()));
            case TOOL_RESULT_STARTED -> {
                var call = toolCalls.get(key);
                if (call != null) {
                    call.executionStartedAt = event.createdAt();
                }
            }
            case TOOL_CALL_COMPLETED ->
                    completeTool(
                            key,
                            values,
                            event.createdAt(),
                            Boolean.TRUE.equals(values.get(ToolGatewayPort.CONTROL_HANDOFF_KEY))
                                    ? "已交接"
                                    : "成功");
            case TOOL_CALL_FAILED -> completeTool(key, values, event.createdAt(), "失败");
            case AUTHORIZATION_REQUESTED -> completeTool(key, values, event.createdAt(), "待授权");
            default -> {
                // 参数增量等不改变工具调用状态。
            }
        }
    }

    private void completeTool(String key, Map<String, Object> values, Instant at, String outcome) {
        var call =
                toolCalls.computeIfAbsent(
                        key, ignored -> new ToolCall(text(values.get("toolName")), null));
        call.completedAt = at;
        call.outcome = outcome;
    }

    private void terminal(String status, Map<String, Object> values) {
        result = status;
        var reason = values.get("turnEndReason");
        endReason = reason == null ? null : reason.toString();
    }

    private Optional<ModelCall> openModelCall() {
        if (modelCalls.isEmpty()) {
            return Optional.empty();
        }
        var last = modelCalls.getLast();
        return last.completedAt == null ? Optional.of(last) : Optional.empty();
    }

    private void guarded(Runnable action) {
        if (!enabled) {
            return;
        }
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    private static String describe(Throwable failure) {
        if (failure == null) {
            return NONE;
        }
        var message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        var singleLine = message.replaceAll("\\s+", " ").trim();
        if (singleLine.length() > MAX_MESSAGE_LENGTH) {
            singleLine = singleLine.substring(0, MAX_MESSAGE_LENGTH) + "...";
        }
        return failure.getClass().getSimpleName() + " " + singleLine;
    }

    private static String text(Object value) {
        return value == null ? NONE : value.toString();
    }

    private static String orNone(String value) {
        return value == null ? NONE : value;
    }

    private static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.1fs", millis / 1000.0);
    }

    private static String between(Instant from, Instant to) {
        if (from == null || to == null) {
            return NONE;
        }
        return seconds(Duration.between(from, to).toMillis());
    }

    /** 协调者委派出的子节点；只含节点键、类型、角色与状态。 */
    record ChildNode(String nodeId, String kind, String roleKey, String status) {
        String describe() {
            return "%s[%s] 角色=%s 状态=%s"
                    .formatted(nodeId, kind, roleKey == null ? NONE : roleKey, orNone(status));
        }
    }

    private static final class ModelCall {
        private final Instant startedAt;
        private Instant firstOutputAt;
        private Instant completedAt;
        private String outcome;
        private Object modelId;
        private Object inputTokens;
        private Object outputTokens;
        private Object durationSeconds;

        private ModelCall(Instant startedAt) {
            this.startedAt = startedAt;
        }

        private void firstOutput(Instant at) {
            if (firstOutputAt == null) {
                firstOutputAt = at;
            }
        }

        private void complete(Instant at, boolean failed, Map<String, Object> values) {
            completedAt = at;
            outcome = failed ? "失败" : null;
            modelId = values.get("modelId");
            inputTokens = values.get("inputTokens");
            outputTokens = values.get("outputTokens");
            durationSeconds = values.get("durationSeconds");
        }

        private String describe(String profileModelId) {
            var model = modelId == null ? orNone(profileModelId) : modelId.toString();
            var total =
                    startedAt != null && completedAt != null
                            ? between(startedAt, completedAt)
                            : durationSeconds instanceof Number number
                                    ? seconds(Math.round(number.doubleValue() * 1000))
                                    : NONE;
            var line =
                    "model=%s 首字 %s 总耗时 %s in=%s out=%s"
                            .formatted(
                                    model,
                                    between(startedAt, firstOutputAt),
                                    completedAt == null ? "未结束" : total,
                                    text(inputTokens),
                                    text(outputTokens));
            return outcome == null ? line : line + " " + outcome;
        }
    }

    private static final class ToolCall {
        private final String name;
        private final Instant startedAt;
        private Instant executionStartedAt;
        private Instant completedAt;
        private String outcome;

        private ToolCall(String name, Instant startedAt) {
            this.name = name;
            this.startedAt = startedAt;
        }

        private String describe() {
            var from = executionStartedAt != null ? executionStartedAt : startedAt;
            return "%s %s %s"
                    .formatted(
                            name,
                            completedAt == null ? "未结束" : between(from, completedAt),
                            orNone(outcome));
        }
    }
}
