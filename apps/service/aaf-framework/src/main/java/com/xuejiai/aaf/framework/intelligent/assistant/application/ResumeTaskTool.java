package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** PRIMARY Assistant 按用户明确指令恢复指定 canonical Task。 */
public final class ResumeTaskTool implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "resume_task";

    private final TaskCommandService tasks;

    public ResumeTaskTool(TaskCommandService tasks) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "恢复用户明确指定、已完成 durable pause 的 Task。系统严格按已冻结的恢复模式复用同一 attempt 或创建 fresh attempt。";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                        "taskId",
                        Map.of(
                                "type",
                                "string",
                                "description",
                                "明确目标 Task 时填写；省略时仅允许当前对话唯一活跃 Task")));
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> resume(invocation)).subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult resume(ToolInvocation invocation) {
        var context = invocation.context();
        if (context.taskId() != null) {
            throw new IllegalStateException("resume_task 只能由新的 PRIMARY 对话 Execution 调用");
        }
        var task =
                tasks.resolveControllableTask(
                        context.tenantId(),
                        context.userId(),
                        context.conversationId(),
                        optionalText(invocation.arguments().get("taskId")));
        var resumed = tasks.resume(context.tenantId(), context.userId(), task.taskId());
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("taskId", resumed.taskId().value());
        metadata.put("taskStatus", resumed.status().name());
        return new ToolInvocationResult("Task 已按持久恢复模式重新进入调度。", metadata);
    }

    private static String optionalText(Object value) {
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }
}
