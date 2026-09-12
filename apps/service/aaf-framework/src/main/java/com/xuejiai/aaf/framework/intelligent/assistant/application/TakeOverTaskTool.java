package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** PRIMARY Assistant 按用户明确指令接管指定 canonical Task。 */
public final class TakeOverTaskTool implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "take_over_task";

    private final TaskCommandService tasks;

    public TakeOverTaskTool(TaskCommandService tasks) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "接管用户明确指定的 Task。系统先关闭旧执行权并完成 durable pause，" + "只有到达稳定 PAUSED 后才把责任主体切换为当前用户。";
    }

    /** 只改变 AAF 运行控制面，不反向调用业务 Tool。 */
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
                                "reason", Map.of("type", "string", "description", "用户接管任务的原因")),
                "required", java.util.List.of("reason"));
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> takeOver(invocation))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult takeOver(ToolInvocation invocation) {
        var context = invocation.context();
        if (context.taskId() != null) {
            throw new IllegalStateException("take_over_task 只能由新的 PRIMARY 对话 Execution 调用");
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
        var takenOver = tasks.takeOver(context.tenantId(), context.userId(), task.taskId(), reason);
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("taskId", takenOver.taskId().value());
        metadata.put("taskStatus", takenOver.status().name());
        metadata.put("ownerKind", takenOver.owner().kind().name());
        return new ToolInvocationResult(
                takenOver.status() == Task.Status.PAUSED
                        ? "Task 已稳定暂停并由当前用户接管。"
                        : "Task 已进入接管流程；旧执行权已关闭，责任主体将在暂停收敛后切换。",
                metadata);
    }

    private static String optionalText(Object value) {
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }
}
