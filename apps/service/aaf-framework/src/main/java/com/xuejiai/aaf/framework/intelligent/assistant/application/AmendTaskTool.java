package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;

import reactor.core.publisher.Mono;

/** PRIMARY Assistant 将明确的用户调整请求提交到 canonical Task 控制面。 */
public final class AmendTaskTool implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "amend_task";

    private final TaskCommandService tasks;
    private final Clock clock;

    public AmendTaskTool(TaskCommandService tasks, Clock clock) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "按用户明确要求调整已有 Task。修改子智能体、任务、依赖或整体目标时使用 TASK_PLAN；"
                + "只修改某个子智能体的内部步骤列表时使用 EXECUTOR_PLAN。无法唯一确定 Task 或节点时不得调用，应先追问。";
    }

    /** 只改变 AAF 运行控制面，不直接调用业务 Tool 或删除用户数据。 */
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
                                "scope",
                                        Map.of(
                                                "type",
                                                "string",
                                                "enum",
                                                java.util.List.of("TASK_PLAN", "EXECUTOR_PLAN")),
                                "nodeId",
                                        Map.of(
                                                "type",
                                                "string",
                                                "description",
                                                "scope=EXECUTOR_PLAN 时必填"),
                                "change",
                                        Map.of(
                                                "type",
                                                "string",
                                                "description",
                                                "用户要求的新增、查询外的修改或删除内容")),
                "required", java.util.List.of("scope", "change"));
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> request(invocation))
                .flatMap(
                        request ->
                                tasks.acceptInput(request.input())
                                        .map(
                                                task -> {
                                                    var metadata =
                                                            new LinkedHashMap<String, Object>();
                                                    metadata.put("taskId", task.taskId().value());
                                                    metadata.put(
                                                            "taskStatus", task.status().name());
                                                    metadata.put("scope", request.scope());
                                                    if (task.currentPlanRevision() != null) {
                                                        metadata.put(
                                                                "currentPlanRevision",
                                                                task.currentPlanRevision());
                                                    }
                                                    return new ToolInvocationResult(
                                                            request.scope().equals("TASK_PLAN")
                                                                    ? "任务已进入重新规划，新计划将在旧执行权关闭后接管。"
                                                                    : "目标子智能体的旧执行计划已停止，将以新 attempt 重新规划步骤。",
                                                            metadata);
                                                }));
    }

    private Request request(ToolInvocation invocation) {
        var context = invocation.context();
        if (context.taskId() != null) {
            throw new IllegalStateException("amend_task 只能由新的 PRIMARY 对话 Execution 调用");
        }
        var scope = requireText(invocation.arguments(), "scope");
        if (!scope.equals("TASK_PLAN") && !scope.equals("EXECUTOR_PLAN")) {
            throw new IllegalArgumentException("scope 必须是 TASK_PLAN 或 EXECUTOR_PLAN");
        }
        var change = requireText(invocation.arguments(), "change");
        var nodeId = optionalText(invocation.arguments().get("nodeId"));
        if (scope.equals("EXECUTOR_PLAN") && nodeId == null) {
            throw new IllegalArgumentException("调整 ExecutorPlan 必须明确 nodeId");
        }
        var task =
                tasks.resolveControllableTask(
                        context.tenantId(),
                        context.userId(),
                        context.conversationId(),
                        optionalText(invocation.arguments().get("taskId")));
        var values = new LinkedHashMap<String, String>();
        values.put("scope", scope);
        if (nodeId != null) {
            values.put("nodeId", nodeId);
        }
        var input =
                new ExecutionInput(
                        "amend-task:" + invocation.toolCallId(),
                        context.tenantId(),
                        context.userId(),
                        task.taskId(),
                        null,
                        ExecutionInput.Kind.MODIFY,
                        change,
                        values,
                        clock.instant());
        return new Request(input, scope);
    }

    private static String requireText(Map<String, Object> arguments, String field) {
        var value = optionalText(arguments.get(field));
        if (value == null) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value;
    }

    private static String optionalText(Object value) {
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    private record Request(ExecutionInput input, String scope) {}
}
