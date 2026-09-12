package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** PRIMARY Assistant 按用户明确指令暂停指定 canonical Task。 */
public final class PauseTaskTool implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "pause_task";

    private final TaskCommandService tasks;

    public PauseTaskTool(TaskCommandService tasks) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "暂停用户明确指定的 Task。请求会立即关闭旧执行权并进入 PAUSING；"
                + "系统逐 Execution 保存恢复状态，失败或超时会安全降级为 fresh attempt。";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties",
                        Map.of(
                                "taskId",
                                        Map.of(
                                                "type",
                                                "string",
                                                "description",
                                                "明确目标 Task 时填写；省略时仅允许当前对话唯一活跃 Task"),
                                "reason", Map.of("type", "string", "description", "用户要求暂停任务的原因")),
                "required", java.util.List.of("reason"));
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> pause(invocation)).subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult pause(ToolInvocation invocation) {
        var context = invocation.context();
        if (context.taskId() != null) {
            throw new IllegalStateException("pause_task 只能由新的 PRIMARY 对话 Execution 调用");
        }
        var reason = optionalText(invocation.arguments().get("reason"));
        if (reason == null) {
            throw new IllegalArgumentException("reason 不能为空白");
        }
        var task =
                tasks.resolveControllableTask(
                        context.tenantId(),
                        context.userId(),
                        context.conversationId(),
                        optionalText(invocation.arguments().get("taskId")));
        var paused = tasks.pause(context.tenantId(), context.userId(), task.taskId(), reason);
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("taskId", paused.taskId().value());
        metadata.put("taskStatus", paused.status().name());
        return new ToolInvocationResult(
                "Task 已进入暂停流程，旧执行权已关闭；状态保存失败或超时会安全降级为 fresh attempt。", metadata);
    }

    private static String optionalText(Object value) {
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }
}
