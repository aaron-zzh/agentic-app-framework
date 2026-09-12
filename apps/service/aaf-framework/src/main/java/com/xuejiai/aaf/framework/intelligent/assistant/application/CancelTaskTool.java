package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** PRIMARY Assistant 按用户明确指令停止指定 canonical Task。 */
public final class CancelTaskTool implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "cancel_task";

    private final TaskCommandService tasks;

    public CancelTaskTool(TaskCommandService tasks) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "停止用户明确指定的 Task。无法唯一确定目标时不得调用，应先追问。"
                + "请求会立即关闭旧执行权并进入 CANCELING，由系统异步终结；"
                + "计划历史、审计事实和用户业务数据始终保留。";
    }

    /** 只改变 AAF 运行控制面，不反向调用业务 Tool 或删除用户数据。 */
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
                                "reason", Map.of("type", "string", "description", "用户要求停止任务的原因")),
                "required", java.util.List.of("reason"));
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> cancel(invocation)).subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult cancel(ToolInvocation invocation) {
        var context = invocation.context();
        if (context.taskId() != null) {
            throw new IllegalStateException("cancel_task 只能由新的 PRIMARY 对话 Execution 调用");
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
        var canceled = tasks.cancel(context.tenantId(), context.userId(), task.taskId(), reason);
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("taskId", canceled.taskId().value());
        metadata.put("taskStatus", canceled.status().name());
        return new ToolInvocationResult(
                "Task 已进入取消流程，旧执行权已关闭；系统将异步完成终结。" + "计划历史、审计事实和用户业务数据均已保留。", metadata);
    }

    private static String optionalText(Object value) {
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }
}
