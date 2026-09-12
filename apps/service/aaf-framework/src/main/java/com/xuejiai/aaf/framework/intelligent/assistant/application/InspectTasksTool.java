package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskQueryPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.plan.ExecutorPlanPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** PRIMARY Assistant 查询当前用户 Task、TaskNode 与 ExecutorPlan 步骤的安全投影。 */
public final class InspectTasksTool implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "inspect_tasks";

    private final TaskQueryPort tasks;
    private final ExecutorPlanPort executorPlans;

    public InspectTasksTool(TaskQueryPort tasks, ExecutorPlanPort executorPlans) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
        this.executorPlans = Objects.requireNonNull(executorPlans, "executorPlans 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "读取当前用户在本对话中的 Task、子智能体节点和可选局部步骤列表。"
                + "当用户没有明确 taskId/nodeId 或询问进度、任务列表、步骤列表时先调用本工具。";
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
                        "taskId", Map.of("type", "string"),
                        "nodeId", Map.of("type", "string")));
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> inspect(invocation))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult inspect(ToolInvocation invocation) {
        var context = invocation.context();
        if (context.taskId() != null) {
            throw new IllegalStateException("inspect_tasks 只供 PRIMARY 对话 Execution 使用");
        }
        var requestedTaskId = optionalText(invocation.arguments().get("taskId"));
        var requestedNodeId = optionalText(invocation.arguments().get("nodeId"));
        var details =
                requestedTaskId == null
                        ? tasks.list(context.tenantId(), context.userId(), context.conversationId())
                        : tasks
                                .find(
                                        context.tenantId(),
                                        context.userId(),
                                        new TaskId(requestedTaskId))
                                .filter(
                                        task ->
                                                task.conversationId()
                                                        .equals(context.conversationId().value()))
                                .stream()
                                .toList();
        var projections = new ArrayList<Map<String, Object>>();
        for (var task : details) {
            var projection = new LinkedHashMap<String, Object>();
            projection.put("taskId", task.taskId());
            projection.put("status", task.status());
            projection.put("goal", task.goal());
            if (task.plan() != null) {
                projection.put("plan", task.plan());
            }
            if (task.rootResult() != null) {
                projection.put("rootResult", task.rootResult());
            }
            if (requestedNodeId != null) {
                var active =
                        executorPlans
                                .findActive(
                                        context.tenantId(),
                                        new TaskId(task.taskId()),
                                        requestedNodeId)
                                .orElse(null);
                if (active != null) {
                    projection.put("executorPlan", active);
                    projection.put(
                            "steps", executorPlans.findSteps(context.tenantId(), active.planId()));
                }
            }
            projections.add(Map.copyOf(projection));
        }
        var metadata = Map.<String, Object>of("tasks", java.util.List.copyOf(projections));
        return new ToolInvocationResult(
                projections.isEmpty() ? "当前对话没有匹配的 Task。" : "已读取匹配的 Task、节点和步骤；如存在多个候选，请先让用户明确目标。",
                metadata);
    }

    private static String optionalText(Object value) {
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }
}
