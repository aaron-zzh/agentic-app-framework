package com.xuejiai.aaf.framework.intelligent.shared.event;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 从既有事件流聚合回合度量的纯函数。
 *
 * <p>不订阅额外遥测：模型用量已由 AgentScope 事件映射写入 {@code MODEL_CALL_COMPLETED} payload，工具与子任务计数直接数事件。
 */
public final class TurnOutcomeAggregator {

    private TurnOutcomeAggregator() {}

    public static TurnOutcome aggregate(TurnEndReason endReason, List<ExecutionEvent> events) {
        Objects.requireNonNull(endReason, "endReason 不能为空");
        Objects.requireNonNull(events, "events 不能为空");
        var modelInvocations = 0;
        var toolCalls = 0;
        // 恒为 0：原用于统计的 SUBTASK_CREATED 事件类型已删除（AAF-104 核实确认从未被任何生产代码发出，是
        // DelegatedTaskCoordinator 落地前预留、后被 EXECUTION_STARTED+nodeIdentity 模式取代的孤儿枚举值）。
        // 字段本身保留（不改 TurnOutcome 签名），避免超出本次任务范围的下游改动。
        var subTaskCount = 0;
        long inputTokens = 0;
        long outputTokens = 0;
        long cachedInputTokens = 0;
        for (var event : events) {
            switch (event.type()) {
                case MODEL_CALL_COMPLETED -> {
                    modelInvocations++;
                    inputTokens += number(event, "inputTokens");
                    outputTokens += number(event, "outputTokens");
                    cachedInputTokens += number(event, "cachedTokens");
                }
                case MODEL_CALL_FAILED -> modelInvocations++;
                case TOOL_CALL_COMPLETED, TOOL_CALL_FAILED -> toolCalls++;
                default -> {
                    // 其余事件不参与度量聚合。
                }
            }
        }
        return new TurnOutcome(
                endReason,
                modelInvocations,
                toolCalls,
                subTaskCount,
                inputTokens,
                outputTokens,
                cachedInputTokens,
                span(events));
    }

    private static Duration span(List<ExecutionEvent> events) {
        if (events.isEmpty()) {
            return Duration.ZERO;
        }
        var earliest = events.getFirst().createdAt();
        var latest = earliest;
        for (var event : events) {
            if (event.createdAt().isBefore(earliest)) {
                earliest = event.createdAt();
            }
            if (event.createdAt().isAfter(latest)) {
                latest = event.createdAt();
            }
        }
        return Duration.between(earliest, latest);
    }

    private static long number(ExecutionEvent event, String key) {
        var value = event.payload().values().get(key);
        return value instanceof Number found ? found.longValue() : 0L;
    }
}
