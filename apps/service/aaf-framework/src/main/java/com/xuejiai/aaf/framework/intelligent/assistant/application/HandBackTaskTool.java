package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** PRIMARY Assistant 按用户明确指令把 canonical Task 交回合同 Owner Assistant。 */
public final class HandBackTaskTool implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "hand_back_task";

    private final TaskCommandService tasks;

    public HandBackTaskTool(TaskCommandService tasks) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "把当前用户已接管且稳定 PAUSED 的 Task 交回合同 Owner Assistant。"
                + "系统强制创建 fresh attempt，不复用人工接管前的 AgentState。";
    }

    /** 只改变 AAF 运行控制面，不反向调用业务 Tool。 */
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
        return Mono.fromCallable(() -> handBack(invocation))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult handBack(ToolInvocation invocation) {
        var context = invocation.context();
        if (context.taskId() != null) {
            throw new IllegalStateException("hand_back_task 只能由新的 PRIMARY 对话 Execution 调用");
        }
        var task =
                tasks.resolveControllableTask(
                        context.tenantId(),
                        context.userId(),
                        context.conversationId(),
                        optionalText(invocation.arguments().get("taskId")));
        var handedBack = tasks.handBack(context.tenantId(), context.userId(), task.taskId());
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("taskId", handedBack.taskId().value());
        metadata.put("taskStatus", handedBack.status().name());
        metadata.put("ownerKind", handedBack.owner().kind().name());
        return new ToolInvocationResult(
                "Task 已交回合同 Owner Assistant，并通过 fresh attempt 重新进入调度。", metadata);
    }

    private static String optionalText(Object value) {
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }
}
