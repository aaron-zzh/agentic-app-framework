package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask.OwnerKind;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask.RecoveryPoint;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask.TaskActor;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask.TaskOwner;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask.TaskStatus;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskControlPort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 将当前 Assistant 任务安全移交人工支持队列的内置工具。 */
public final class SupportHandoffTool implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "support.handoff";
    private static final String SUPPORT_QUEUE_OWNER_ID = "support-queue";

    private final TaskControlPort tasks;
    private final ExecutionEventStorePort events;

    public SupportHandoffTool(TaskControlPort tasks, ExecutionEventStorePort events) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
        this.events = Objects.requireNonNull(events, "events 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "将当前任务移交人工支持队列。仅提供简短、脱敏的交接原因；调用后任务暂停，等待人工处理或恢复。";
    }

    /** 转移任务责任主体至人工队列，有真实副作用（`TaskStatus.PAUSED` + owner 切换），不可撤销回滚为自动执行前状态。 */
    @Override
    public boolean requireConfirm() {
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
                        Map.of(
                                "type",
                                "string",
                                "description",
                                "简短、脱敏的交接原因，不超过 256 个字符")),
                "required",
                List.of("reason"));
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> handoff(invocation))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(
                        handoff ->
                                events.append(handoff.event(), invocation.context().lease())
                                        .thenReturn(handoff.result()));
    }

    private HandoffResult handoff(ToolInvocation invocation) {
        var context = invocation.context();
        var reason = requireReason(invocation.arguments());
        var task =
                tasks.find(context.tenantId(), context.taskId())
                        .orElseThrow(() -> new IllegalStateException("人工交接关联任务不存在"));
        if (task.status() != TaskStatus.RUNNING) {
            throw new IllegalStateException("仅运行中的任务可以移交人工支持: " + task.status());
        }

        var now = Instant.now();
        var paused =
                task.transitionTo(
                        TaskStatus.PAUSED,
                        "已请求人工支持：" + reason,
                        new TaskActor(OwnerKind.AGENT, context.assistantId().value()),
                        new TaskOwner(OwnerKind.HUMAN, SUPPORT_QUEUE_OWNER_ID),
                        new RecoveryPoint("support-handoff", "人工支持处理后从冻结执行画像恢复"),
                        now);
        var saved = tasks.save(context.tenantId(), paused, context.lease());
        var values = new LinkedHashMap<String, Object>();
        values.put("handoffReason", reason);
        values.put("taskStatus", saved.status().name());
        values.put("ownerKind", saved.owner().kind().name());
        values.put("ownerId", saved.owner().ownerId());
        values.put("recoveryKey", "support-handoff");
        values.put("toolName", TOOL_NAME);
        var event =
                new ExecutionEvent(
                        new EventId("support-handoff-" + invocation.toolCallId()),
                        context.tenantId(),
                        context.conversationId(),
                        context.sessionId(),
                        context.taskId(),
                        context.executionId(),
                        context.runId(),
                        context.parentExecutionId(),
                        0,
                        ExecutionEventType.OWNERSHIP_TRANSFERRED,
                        ExecutionEventStatus.PAUSED,
                        context.controlMode(),
                        OwnerType.HUMAN,
                        context.assistantId(),
                        null,
                        context.userId(),
                        context.correlationId(),
                        context.causationId(),
                        context.idempotencyKey(),
                        new ExecutionEventPayload(values),
                        now);
        var output =
                JsonUtils.toJsonString(
                        Map.of(
                                "status", "HANDOFF_REQUESTED",
                                "taskStatus", saved.status().name(),
                                "recoveryKey", "support-handoff"));
        return new HandoffResult(new ToolInvocationResult(output, Map.of("handoff", true)), event);
    }

    private static String requireReason(Map<String, Object> arguments) {
        var value = arguments.get("reason");
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("support.handoff 需要非空 reason");
        }
        var reason = value.toString().trim();
        if (reason.length() > 256) {
            throw new IllegalArgumentException("support.handoff reason 不能超过 256 个字符");
        }
        return reason;
    }

    private record HandoffResult(ToolInvocationResult result, ExecutionEvent event) {}
}
