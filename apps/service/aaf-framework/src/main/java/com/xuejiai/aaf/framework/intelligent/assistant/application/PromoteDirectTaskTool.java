package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 当前 Assistant 将尚无副作用的 DIRECT execution 提升为 canonical Task 的唯一模型入口。 */
public final class PromoteDirectTaskTool implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "promote_direct_task";

    private final TaskCommandService tasks;

    public PromoteDirectTaskTool(TaskCommandService tasks) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "当当前目标确实需要多步骤、持久恢复或结构化人工交互时，将本次 DIRECT execution 提升为持久 Task。"
                + "普通问答、自然澄清和可在本回合完成的目标不得调用。";
    }

    /** 仅改变 AAF 控制面形态，不执行外部业务动作，也不扩大工具、Role、预算或授权。 */
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
                        "reason",
                        Map.of("type", "string", "description", "需要多步骤、持久恢复或结构化人工交互的具体原因")),
                "required",
                java.util.List.of("reason"));
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> promote(invocation))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult promote(ToolInvocation invocation) {
        var context = invocation.context();
        if (context.taskId() != null || context.nodeIdentity() != null) {
            throw new IllegalStateException("promote_direct_task 仅允许 DIRECT execution 调用");
        }
        var reason = invocation.arguments().get("reason");
        if (!(reason instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("promotion reason 不能为空白");
        }
        var normalizedReason = text.trim();
        if (normalizedReason.length() > 512) {
            throw new IllegalArgumentException("promotion reason 不能超过 512 字符");
        }
        var task = tasks.promoteDirect(context.tenantId(), context.executionId(), normalizedReason);
        var values = new LinkedHashMap<String, Object>();
        values.put("taskId", task.taskId().value());
        values.put("taskStatus", task.status().name());
        values.put("originExecutionId", context.executionId().value());
        return new ToolInvocationResult("当前目标已提升为持久 Task，后续由 Task Owner Assistant 继续规划。", values);
    }
}
