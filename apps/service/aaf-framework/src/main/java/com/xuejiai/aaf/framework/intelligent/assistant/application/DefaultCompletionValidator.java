package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask.RecoveryPoint;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionDecision;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionDecision.Outcome;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;

/** 基于显式业务证据验证完成，不把 AGENT_END 或流结束直接当作任务完成。 */
public final class DefaultCompletionValidator implements CompletionValidator {

    @Override
    public CompletionDecision validate(ValidationRequest request) {
        var events = request.events();
        if (events.stream()
                .anyMatch(
                        event ->
                                event.type() == ExecutionEventType.RUN_FAILED
                                        || event.status() == ExecutionEventStatus.FAILED)) {
            return decision(Outcome.FAILED, "Agent 执行失败", "agent-failure");
        }
        if (events.stream()
                .anyMatch(
                        event ->
                                event.status() == ExecutionEventStatus.AWAITING_AUTHORIZATION
                                        || event.status() == ExecutionEventStatus.AWAITING_INPUT)) {
            return decision(Outcome.NEEDS_USER, "任务需要用户输入或授权", "user-input");
        }
        if (events.stream().anyMatch(event -> event.status() == ExecutionEventStatus.PAUSED)) {
            return decision(Outcome.HANDOFF, "任务已暂停，等待人工处理", "human-handoff");
        }
        if (events.stream().noneMatch(event -> event.type() == ExecutionEventType.RUN_COMPLETED)) {
            return decision(Outcome.CONTINUE_REPAIR, "Agent 回合未形成可验证结果", "agent-retry");
        }

        var missingTypes =
                request.criteria().requiredEventTypes().stream()
                        .filter(
                                required ->
                                        events.stream().noneMatch(event -> event.type() == required))
                        .toList();
        if (!missingTypes.isEmpty()) {
            return decision(
                    Outcome.CONTINUE_REPAIR,
                    "缺少业务完成事件: " + missingTypes,
                    "completion-evidence");
        }

        var missingPayload =
                request.criteria().requiredPayloadValues().entrySet().stream()
                        .filter(
                                required ->
                                        events.stream()
                                                .map(event -> event.payload().values().get(required.getKey()))
                                                .noneMatch(value -> Objects.equals(value, required.getValue())))
                        .map(java.util.Map.Entry::getKey)
                        .toList();
        if (!missingPayload.isEmpty()) {
            return decision(
                    Outcome.CONTINUE_REPAIR,
                    "缺少业务完成证据: " + missingPayload,
                    "completion-evidence");
        }
        return new CompletionDecision(Outcome.COMPLETED, "显式业务完成条件已满足", null);
    }

    private static CompletionDecision decision(
            Outcome outcome, String reason, String recoveryKey) {
        return new CompletionDecision(
                outcome, reason, new RecoveryPoint(recoveryKey, reason));
    }
}
