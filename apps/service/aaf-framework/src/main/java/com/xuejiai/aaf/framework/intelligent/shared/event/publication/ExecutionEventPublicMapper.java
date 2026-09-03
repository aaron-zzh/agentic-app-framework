package com.xuejiai.aaf.framework.intelligent.shared.event.publication;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort.StoredExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.publication.AafAiTaskEvent.Delivery;
import com.xuejiai.aaf.framework.intelligent.shared.event.publication.AafAiTaskEvent.ReferenceSafeData;

/** 执行事实的唯一公共脱敏映射器；绝不透传内部 payload。 */
@Component
public final class ExecutionEventPublicMapper {

    public AafAiTaskEvent map(ExecutionEvent event) {
        return map(event, null, Delivery.LIVE_BEST_EFFORT);
    }

    public AafAiTaskEvent map(StoredExecutionEvent stored) {
        return map(stored.event(), stored.eventOffset(), Delivery.CURSOR_AT_LEAST_ONCE);
    }

    public AafAiTaskEvent projectionFailure(
            AafAiTaskEvent context, String executionId, long sequence, String errorCode) {
        return projectionTerminal(
                context,
                executionId,
                sequence,
                "FAILED",
                "aaf.task.failed",
                "PUBLIC_PROJECTION_FAILED",
                errorCode);
    }

    public AafAiTaskEvent projectionClosed(
            AafAiTaskEvent context, String executionId, long sequence, String status) {
        return projectionTerminal(
                context,
                executionId,
                sequence,
                status,
                "aaf.stream.closed",
                "PUBLIC_STREAM_CLOSED",
                "ASSISTANT_STREAM_CLOSED");
    }

    private AafAiTaskEvent projectionTerminal(
            AafAiTaskEvent context,
            String executionId,
            long sequence,
            String status,
            String type,
            String summaryCode,
            String errorCode) {
        var fallback = safeReference(executionId);
        return new AafAiTaskEvent(
                "projection:" + UUID.randomUUID(),
                context == null ? fallback : context.taskId(),
                context == null ? fallback : context.executionId(),
                context == null ? fallback : context.runId(),
                context == null ? fallback : context.sessionId(),
                Math.max(context == null ? 1 : context.sequence() + 1, sequence),
                null,
                status,
                type,
                1,
                AafAiTaskEvent.Audience.END_USER,
                Delivery.PROJECTION_ONLY,
                new ReferenceSafeData(
                        Map.of("summaryCode", summaryCode, "errorCode", safeCode(errorCode))),
                Instant.now(),
                // 投影兜底事件不属于任何执行或板上节点
                null,
                null);
    }

    private AafAiTaskEvent map(ExecutionEvent event, Long eventOffset, Delivery delivery) {
        var descriptor = AafAiTaskEventRegistry.descriptor(event.type());
        return new AafAiTaskEvent(
                event.eventId().value(),
                event.taskId().value(),
                event.executionId().value(),
                event.runId().value(),
                event.sessionId().value(),
                event.sequence(),
                eventOffset,
                event.status().name(),
                descriptor.type(),
                descriptor.version(),
                descriptor.audience(),
                delivery,
                new ReferenceSafeData(safeData(event)),
                event.createdAt(),
                // 透出节点身份与父执行：AG-UI 据此区分交付类节点与内部节点并合成 source 路径；
                // 暴露的是 subTaskId / roleKey / skillKey 这类稳定或展示用标签，不含原始 agentId
                event.parentExecutionId() == null ? null : event.parentExecutionId().value(),
                event.nodeIdentity());
    }

    private static Map<String, Object> safeData(ExecutionEvent event) {
        var source = event.payload().values();
        var safe = new LinkedHashMap<String, Object>();
        safe.put("summaryCode", event.type().name());
        copyText(source, safe, "skillKey");
        copyText(source, safe, "taskStatus");
        copyText(source, safe, "phase");
        copyText(source, safe, "stage");
        switch (event.type()) {
            case MESSAGE_STARTED -> safe.put("messageId", event.executionId().value());
            case MESSAGE_DELTA -> {
                safe.put("messageId", event.executionId().value());
                copyText(source, safe, "replyId");
                safe.put("contentLength", textLength(source.get("delta")));
            }
            case MESSAGE_COMPLETED -> {
                safe.put("messageId", event.executionId().value());
                copyText(source, safe, "replyId");
                safe.put("contentLength", textLength(source.get("text")));
            }
            case TOOL_CALL_STARTED, TOOL_CALL_COMPLETED, TOOL_CALL_FAILED -> {
                copyText(source, safe, "toolCallId");
                copyText(source, safe, "toolName");
                copyText(source, safe, "resultState");
                copyText(source, safe, "artifactState");
                copyText(source, safe, "artifactType");
                copyPositiveLong(source, safe, "artifactId");
                copyBoolean(source, safe, "reversible");
            }
            case AUTHORIZATION_REQUESTED,
                    AUTHORIZATION_GRANTED,
                    AUTHORIZATION_DENIED,
                    AUTHORIZATION_REVOKED,
                    APPROVAL_REQUESTED,
                    APPROVAL_RESOLVED -> {
                copyText(source, safe, "approvalId");
                copyText(source, safe, "toolCallId");
                copyText(source, safe, "toolName");
                copyText(source, safe, "action");
                copyText(source, safe, "decision");
                copyBoolean(source, safe, "reversible");
            }
            case CLARIFICATION_REQUESTED,
                    CLARIFICATION_UPDATED,
                    CLARIFICATION_RESOLVED,
                    CLARIFICATION_CANCELED,
                    CLARIFICATION_EXPIRED -> {
                copyText(source, safe, "requestId");
                copyText(source, safe, "subTaskId");
                copyPositiveLong(source, safe, "completedFieldCount");
                copyPositiveLong(source, safe, "requiredFieldCount");
            }
            case ITERATION_EVALUATED, ITERATION_STOPPED -> {
                copyText(source, safe, "groupId");
                copyPositiveLong(source, safe, "iteration");
                copyText(source, safe, "decision");
                copyText(source, safe, "stopReason");
            }
            case COMMAND_REJECTED -> safe.put("errorCode", "ASSISTANT_COMMAND_REJECTED");
            case RUN_FAILED, EXECUTION_FAILED ->
                    safe.put("errorCode", "ASSISTANT_EXECUTION_FAILED");
            case ROLE_RESOLVED -> {
                copyText(source, safe, "roleKey");
                copyText(source, safe, "roleName");
                copyText(source, safe, "routeConstraint");
                copyText(source, safe, "interactionMode");
            }
            default -> {
                // 未显式注册的 payload 字段一律不公开。
            }
        }
        return safe;
    }

    private static void copyText(
            Map<String, Object> source, Map<String, Object> target, String key) {
        var value = source.get(key);
        if (value instanceof String text && !text.isBlank() && !text.contains("\n")) {
            target.put(key, text.length() <= 256 ? text : text.substring(0, 256));
        }
    }

    private static void copyPositiveLong(
            Map<String, Object> source, Map<String, Object> target, String key) {
        var value = source.get(key);
        if (value instanceof Number number && number.longValue() >= 0) {
            target.put(key, number.longValue());
        }
    }

    private static void copyBoolean(
            Map<String, Object> source, Map<String, Object> target, String key) {
        var value = source.get(key);
        if (value instanceof Boolean flag) {
            target.put(key, flag);
        }
    }

    private static int textLength(Object value) {
        if (!(value instanceof String text)) {
            return 0;
        }
        return text.codePointCount(0, text.length());
    }

    private static String safeReference(String value) {
        return value == null || value.isBlank() ? "unknown:" + UUID.randomUUID() : value;
    }

    private static String safeCode(String value) {
        if (value == null || !value.matches("[A-Z0-9_.-]{1,128}")) {
            return "ASSISTANT_PUBLIC_PROJECTION_FAILED";
        }
        return value;
    }
}
