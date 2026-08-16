package com.xuejiai.aaf.module.ai.assistant.vo;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;

/** 通用 Assistant 执行事件的稳定安全投影。 */
public record AssistantExecutionEventVO(
        long sequence,
        String type,
        String status,
        Instant createdAt,
        Map<String, Object> payload,
        List<ContextSourceVO> contextSources) {

    public AssistantExecutionEventVO {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        contextSources = contextSources == null ? List.of() : List.copyOf(contextSources);
    }

    public static AssistantExecutionEventVO from(ExecutionEvent event) {
        var values = event.payload().values();
        return new AssistantExecutionEventVO(
                event.sequence(),
                event.type().name(),
                event.status().name(),
                event.createdAt(),
                safePayload(event.type(), values),
                contextSources(values.get("contextSources")));
    }

    public static AssistantExecutionEventVO mockStarted(long sequence) {
        return new AssistantExecutionEventVO(
                sequence,
                "EXECUTION_STARTED",
                "RUNNING",
                Instant.now(),
                Map.of("skillKey", "content.draft"),
                List.of());
    }

    public static AssistantExecutionEventVO mockDelta(long sequence, String delta) {
        return new AssistantExecutionEventVO(
                sequence,
                "MESSAGE_DELTA",
                "RUNNING",
                Instant.now(),
                Map.of("delta", delta),
                List.of());
    }

    public static AssistantExecutionEventVO mockCompleted(long sequence, String text) {
        return new AssistantExecutionEventVO(
                sequence,
                "MESSAGE_COMPLETED",
                "RUNNING",
                Instant.now(),
                Map.of("text", text),
                List.of());
    }

    public static AssistantExecutionEventVO mockExecutionCompleted(long sequence) {
        return new AssistantExecutionEventVO(
                sequence,
                "EXECUTION_COMPLETED",
                "COMPLETED",
                Instant.now(),
                Map.of("skillKey", "content.draft"),
                List.of());
    }

    public static AssistantExecutionEventVO unexpectedFailure(long sequence) {
        return new AssistantExecutionEventVO(
                sequence,
                "EXECUTION_FAILED",
                "FAILED",
                Instant.now(),
                Map.of("errorCode", "ASSISTANT_EXECUTION_FAILED", "message", "Assistant 执行失败"),
                List.of());
    }

    private static Map<String, Object> safePayload(
            ExecutionEventType type, Map<String, Object> values) {
        var result = new LinkedHashMap<String, Object>();
        copyString(values, result, "skillKey");
        copyString(values, result, "taskStatus");
        copyString(values, result, "phase");
        copyString(values, result, "stage");
        switch (type) {
            case MESSAGE_DELTA -> copyString(values, result, "delta");
            case MESSAGE_COMPLETED -> copyString(values, result, "text");
            case TOOL_CALL_STARTED, TOOL_CALL_COMPLETED, TOOL_CALL_FAILED -> {
                copyString(values, result, "toolCallId");
                copyString(values, result, "toolName");
                copyString(values, result, "resultState");
            }
            case EXECUTION_STARTED,
                    TASK_STATUS_CHANGED,
                    CONTROL_MODE_CHANGED,
                    RECOVERY_STARTED,
                    VALIDATION_STARTED,
                    VALIDATION_COMPLETED,
                    VALIDATION_FAILED,
                    EXECUTION_COMPLETED,
                    EXECUTION_PAUSED,
                    EXECUTION_CANCELED,
                    OWNERSHIP_TRANSFERRED ->
                    putSafeSummary(type, result);
            default -> {
                // 其他事件只暴露上面的安全阶段字段。
            }
        }
        appendError(type, result);
        return Map.copyOf(result);
    }

    private static void putSafeSummary(ExecutionEventType type, Map<String, Object> result) {
        var summary =
                switch (type) {
                    case EXECUTION_STARTED -> "Assistant 执行已开始";
                    case TASK_STATUS_CHANGED -> "任务状态已更新";
                    case CONTROL_MODE_CHANGED -> "执行控制模式已更新";
                    case RECOVERY_STARTED -> "执行恢复已开始";
                    case VALIDATION_STARTED -> "结果验证已开始";
                    case VALIDATION_COMPLETED -> "结果验证已完成";
                    case VALIDATION_FAILED -> "结果验证未通过";
                    case EXECUTION_COMPLETED -> "Assistant 执行已完成";
                    case EXECUTION_PAUSED -> "Assistant 执行已暂停";
                    case EXECUTION_CANCELED -> "Assistant 执行已取消";
                    case OWNERSHIP_TRANSFERRED -> "任务执行主体已切换";
                    default -> null;
                };
        if (summary != null) {
            result.put("summary", summary);
        }
    }

    private static void appendError(ExecutionEventType type, Map<String, Object> result) {
        switch (type) {
            case COMMAND_REJECTED -> {
                result.put("errorCode", "ASSISTANT_COMMAND_REJECTED");
                result.put("message", "Assistant 请求被拒绝");
            }
            case RUN_FAILED, EXECUTION_FAILED -> {
                result.put("errorCode", "ASSISTANT_EXECUTION_FAILED");
                result.put("message", "Assistant 执行失败");
            }
            case TOOL_CALL_FAILED -> {
                result.put("errorCode", "ASSISTANT_TOOL_FAILED");
                result.put("message", "工具调用失败");
            }
            default -> {
                // 非失败事件不输出错误字段。
            }
        }
    }

    private static void copyString(
            Map<String, Object> source, Map<String, Object> target, String key) {
        var value = objectString(source.get(key));
        if (value != null) {
            target.put(key, value);
        }
    }

    private static List<ContextSourceVO> contextSources(Object value) {
        if (!(value instanceof List<?> sources)) {
            return List.of();
        }
        return sources.stream()
                .filter(Map.class::isInstance)
                .map(source -> contextSource((Map<?, ?>) source))
                .toList();
    }

    private static ContextSourceVO contextSource(Map<?, ?> source) {
        return new ContextSourceVO(
                objectString(source.get("type")),
                objectString(source.get("sourceKey")),
                objectString(source.get("version")),
                objectString(source.get("scope")),
                objectString(source.get("reason")),
                objectString(source.get("summary")));
    }

    private static String objectString(Object value) {
        return value instanceof String text ? text : null;
    }

    public record ContextSourceVO(
            String type,
            String sourceKey,
            String version,
            String scope,
            String reason,
            String summary) {}
}
