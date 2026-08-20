package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;

/** 参数补全请求事实；与 HITL 动作授权完全独立。 */
public record ClarificationRequest(
        String requestId,
        TaskId taskId,
        ExecutionId executionId,
        String subTaskId,
        List<String> requiredFields,
        List<Question> questions,
        Instant deadline,
        Status status,
        Map<String, String> values,
        Instant createdAt,
        Instant resolvedAt,
        String resolutionReason) {

    public ClarificationRequest {
        requestId = requireText(requestId, "requestId");
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        subTaskId = requireText(subTaskId, "subTaskId");
        requiredFields = List.copyOf(Objects.requireNonNull(requiredFields, "requiredFields 不能为空"));
        questions = List.copyOf(Objects.requireNonNull(questions, "questions 不能为空"));
        Objects.requireNonNull(deadline, "deadline 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        values = Map.copyOf(Objects.requireNonNull(values, "values 不能为空"));
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
        if (requiredFields.isEmpty()
                || requiredFields.stream().anyMatch(field -> field == null || field.isBlank())
                || requiredFields.stream().distinct().count() != requiredFields.size()) {
            throw new IllegalArgumentException("requiredFields 必须是非空且唯一的字段列表");
        }
        var questionFields = questions.stream().map(Question::field).toList();
        if (!questionFields.equals(requiredFields)) {
            throw new IllegalArgumentException("questions 必须按 requiredFields 顺序逐项覆盖");
        }
        if (!deadline.isAfter(createdAt)) {
            throw new IllegalArgumentException("澄清 deadline 必须晚于创建时间");
        }
        if (!requiredFields.containsAll(values.keySet())) {
            throw new IllegalArgumentException("澄清值包含未声明字段");
        }
        if (values.values().stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("澄清值不能为空白");
        }
        if (status == Status.PENDING && (resolvedAt != null || resolutionReason != null)) {
            throw new IllegalArgumentException("PENDING 澄清不能携带解决事实");
        }
        if (status != Status.PENDING
                && (resolvedAt == null || resolutionReason == null || resolutionReason.isBlank())) {
            throw new IllegalArgumentException("终止澄清必须携带时间与原因");
        }
        if (status == Status.RESOLVED && !values.keySet().containsAll(requiredFields)) {
            throw new IllegalArgumentException("RESOLVED 澄清必须补齐全部 requiredFields");
        }
    }

    public ClarificationRequest apply(Map<String, String> supplied, boolean replace) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("只有 PENDING 澄清可以补充参数");
        }
        var normalized = normalizeValues(supplied);
        if (!requiredFields.containsAll(normalized.keySet())) {
            throw new IllegalArgumentException("补充参数包含未声明字段");
        }
        var merged = new LinkedHashMap<String, String>();
        if (!replace) {
            merged.putAll(values);
        }
        merged.putAll(normalized);
        return copy(Status.PENDING, merged, null, null);
    }

    public boolean complete() {
        return requiredFields.stream().allMatch(values::containsKey);
    }

    public ClarificationRequest resolve(Instant at) {
        if (!complete()) {
            throw new IllegalStateException("requiredFields 尚未补齐");
        }
        return copy(Status.RESOLVED, values, at, "required-fields-completed");
    }

    public ClarificationRequest cancel(Instant at) {
        return copy(Status.CANCELED, values, at, "user-canceled");
    }

    public ClarificationRequest expire(Instant at) {
        return copy(Status.EXPIRED, values, at, "deadline-reached");
    }

    private ClarificationRequest copy(
            Status nextStatus,
            Map<String, String> nextValues,
            Instant nextResolvedAt,
            String nextReason) {
        return new ClarificationRequest(
                requestId,
                taskId,
                executionId,
                subTaskId,
                requiredFields,
                questions,
                deadline,
                nextStatus,
                nextValues,
                createdAt,
                nextResolvedAt,
                nextReason);
    }

    private static Map<String, String> normalizeValues(Map<String, String> source) {
        Objects.requireNonNull(source, "补充参数不能为空");
        var normalized = new LinkedHashMap<String, String>();
        source.forEach(
                (key, value) -> {
                    var normalizedKey = requireText(key, "参数字段");
                    var normalizedValue = requireText(value, "参数值");
                    normalized.put(normalizedKey, normalizedValue);
                });
        return Map.copyOf(normalized);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }

    public record Question(String field, String question, List<String> options) {
        public Question {
            field = requireText(field, "question.field");
            question = requireText(question, "question.question");
            options = List.copyOf(Objects.requireNonNull(options, "question.options 不能为空"));
            if (options.stream().anyMatch(option -> option == null || option.isBlank())
                    || options.stream().distinct().count() != options.size()) {
                throw new IllegalArgumentException("question.options 不能包含空白或重复选项");
            }
        }
    }

    public enum Status {
        PENDING,
        RESOLVED,
        CANCELED,
        EXPIRED
    }
}
