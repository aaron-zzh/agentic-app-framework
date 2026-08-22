package com.xuejiai.aaf.framework.intelligent.shared.event;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一个回合的收口度量，随终态事件写入 payload。
 *
 * <p>所有字段都由现有事件流可直接聚合，不引入新的采集通道，也不保留任何正文。
 *
 * <p>刻意不包含两个字段：物理模型重试次数（{@code LlmClient} 层的路由与 fallback 尚未上报，逻辑调用数不能冒充）和上下文占用率（目前只在 {@code
 * [Prompt预检]} 日志中，未进入事件流）。这两项打通后再扩展，不放占位值。
 */
public record TurnOutcome(
        TurnEndReason endReason,
        int modelInvocations,
        int toolCalls,
        int subTaskCount,
        long inputTokens,
        long outputTokens,
        long cachedInputTokens,
        Duration duration) {

    public TurnOutcome {
        Objects.requireNonNull(endReason, "endReason 不能为空");
        Objects.requireNonNull(duration, "duration 不能为空");
        requireNonNegative(modelInvocations, "modelInvocations");
        requireNonNegative(toolCalls, "toolCalls");
        requireNonNegative(subTaskCount, "subTaskCount");
        requireNonNegative(inputTokens, "inputTokens");
        requireNonNegative(outputTokens, "outputTokens");
        requireNonNegative(cachedInputTokens, "cachedInputTokens");
        if (cachedInputTokens > inputTokens) {
            throw new IllegalArgumentException("cachedInputTokens 不能超过 inputTokens");
        }
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration 不能为负");
        }
    }

    /** 展开为可安全写入 {@link ExecutionEventPayload} 的键值；Duration 序列化为毫秒。 */
    public Map<String, Object> toPayloadValues() {
        var values = new LinkedHashMap<String, Object>();
        values.put("turnEndReason", endReason.name());
        values.put("modelInvocations", modelInvocations);
        values.put("toolCalls", toolCalls);
        values.put("subTaskCount", subTaskCount);
        values.put("inputTokens", inputTokens);
        values.put("outputTokens", outputTokens);
        values.put("cachedInputTokens", cachedInputTokens);
        values.put("durationMillis", duration.toMillis());
        return values;
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " 不能小于 0");
        }
    }
}
