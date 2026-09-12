package com.xuejiai.aaf.framework.intelligent.shared.event.publication;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonValue;

import com.xuejiai.aaf.framework.intelligent.shared.event.NodeIdentity;

/** 面向外部消费者的安全 AI 任务事件信封。 */
public record AafAiTaskEvent(
        String eventId,
        String taskId,
        String executionId,
        String runId,
        String sessionId,
        long sequence,
        Long eventOffset,
        String status,
        String type,
        int version,
        Audience audience,
        Delivery delivery,
        ReferenceSafeData data,
        Instant createdAt,
        String parentExecutionId,
        NodeIdentity nodeIdentity) {

    public AafAiTaskEvent {
        eventId = requireText(eventId, "eventId");
        executionId = requireText(executionId, "executionId");
        runId = requireText(runId, "runId");
        sessionId = requireText(sessionId, "sessionId");
        status = requireText(status, "status");
        type = requireText(type, "type");
        Objects.requireNonNull(audience, "audience 不能为空");
        Objects.requireNonNull(delivery, "delivery 不能为空");
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
        data = data == null ? ReferenceSafeData.empty() : data;
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence 必须大于 0");
        }
        if (eventOffset != null && eventOffset < 1) {
            throw new IllegalArgumentException("eventOffset 必须大于 0");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version 必须大于 0");
        }
        if (!type.startsWith("aaf.")) {
            throw new IllegalArgumentException("公共事件 type 必须使用 aaf.* 命名空间");
        }
    }

    /** 仅任务级明确终态关闭公共流；运行、模型和工具失败仍可恢复。 */
    public boolean terminal() {
        return switch (type) {
            case "aaf.task.completed",
                    "aaf.task.canceled",
                    "aaf.task.failed",
                    "aaf.task.rejected",
                    "aaf.stream.closed" ->
                    true;
            default -> false;
        };
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value;
    }

    public enum Audience {
        END_USER
    }

    /** 明示投递保证，禁止把实时投影描述为 exactly-once。 */
    public enum Delivery {
        CURSOR_AT_LEAST_ONCE,
        LIVE_BEST_EFFORT,
        PROJECTION_ONLY
    }

    /** 仅允许稳定引用、状态码、计数和布尔值，不允许正文或任意嵌套对象。 */
    public record ReferenceSafeData(Map<String, Object> values) {
        private static final int MAX_TEXT_LENGTH = 256;
        private static final Set<String> ALLOWED_KEYS =
                Set.of(
                        "summaryCode",
                        "skillKey",
                        "taskStatus",
                        "phase",
                        "stage",
                        "messageId",
                        "replyId",
                        "contentLength",
                        "toolCallId",
                        "toolName",
                        "resultState",
                        "artifactState",
                        "artifactType",
                        "artifactId",
                        "reversible",
                        "approvalId",
                        "action",
                        "decision",
                        "requestId",
                        "nodeId",
                        "completedFieldCount",
                        "requiredFieldCount",
                        "groupId",
                        "iteration",
                        "stopReason",
                        "errorCode");

        public ReferenceSafeData {
            var source = values == null ? Map.<String, Object>of() : values;
            var copy = new LinkedHashMap<String, Object>(source.size());
            source.forEach((key, value) -> copy.put(validateKey(key), validateValue(key, value)));
            values = Map.copyOf(copy);
        }

        public static ReferenceSafeData empty() {
            return new ReferenceSafeData(Map.of());
        }

        @JsonValue
        public Map<String, Object> values() {
            return values;
        }

        private static String validateKey(String key) {
            if (!ALLOWED_KEYS.contains(key)) {
                throw new IllegalArgumentException("公共事件 data 不允许字段: " + key);
            }
            return key;
        }

        private static Object validateValue(String key, Object value) {
            if (value instanceof String text) {
                if (text.length() > MAX_TEXT_LENGTH
                        || !text.matches("[A-Za-z0-9][A-Za-z0-9:._/-]{0,255}")) {
                    throw new IllegalArgumentException("公共事件 data 文本不是安全引用: " + key);
                }
                return text;
            }
            if (value instanceof Boolean
                    || value instanceof Byte
                    || value instanceof Short
                    || value instanceof Integer
                    || value instanceof Long) {
                return value;
            }
            throw new IllegalArgumentException("公共事件 data 仅允许安全标量: " + key);
        }
    }
}
