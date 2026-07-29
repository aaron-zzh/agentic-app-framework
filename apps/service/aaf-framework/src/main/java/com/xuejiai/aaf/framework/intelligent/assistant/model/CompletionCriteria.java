package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;

/** 业务任务的显式完成条件；Agent 模型回合结束本身不是完成条件。 */
public record CompletionCriteria(
        Kind kind,
        Set<ExecutionEventType> requiredEventTypes,
        Map<String, Object> requiredPayloadValues) {

    public CompletionCriteria {
        Objects.requireNonNull(kind, "CompletionCriteria kind 不能为空");
        requiredEventTypes =
                Set.copyOf(Objects.requireNonNull(requiredEventTypes, "requiredEventTypes 不能为空"));
        requiredPayloadValues =
                Map.copyOf(
                        Objects.requireNonNull(
                                requiredPayloadValues, "requiredPayloadValues 不能为空"));
        if (requiredEventTypes.isEmpty()) {
            throw new IllegalArgumentException("任务完成条件必须至少要求一个业务事件");
        }
    }

    public static CompletionCriteria responseDelivered() {
        return new CompletionCriteria(
                Kind.RESPONSE_DELIVERED,
                Set.of(ExecutionEventType.MESSAGE_COMPLETED, ExecutionEventType.RUN_COMPLETED),
                Map.of());
    }

    public static CompletionCriteria reversibleDraftCreated() {
        return new CompletionCriteria(
                Kind.REVERSIBLE_DRAFT_CREATED,
                Set.of(ExecutionEventType.TOOL_CALL_COMPLETED, ExecutionEventType.RUN_COMPLETED),
                Map.of("artifactState", "DRAFT", "reversible", true));
    }

    public enum Kind {
        RESPONSE_DELIVERED,
        REVERSIBLE_DRAFT_CREATED,
        CUSTOM
    }
}
