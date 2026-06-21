/**
 * Token 用量事件。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.ai.chat;

import java.util.UUID;

/**
 * 每次 LLM 调用后发布的 Token 用量事件，供计量和配额控制使用。
 *
 * <p>{@code capability} 字段用于落库 {@code credit_transaction.category} 与 {@code
 * ai_usage_record.capability}，使用规范小写值（如 {@code "chat"} / {@code "vision"}），与路由层 {@code
 * CapabilityRoutingContext.CAP_*}（大写）解耦。
 */
public record TokenUsageEvent(
        Long userId,
        Long modelId,
        long promptTokens,
        long completionTokens,
        String usageId,
        String capability) {

    /** 兼容旧构造：默认 {@code capability="chat"}，自动生成 usageId。 */
    public TokenUsageEvent(Long userId, Long modelId, long promptTokens, long completionTokens) {
        this(userId, modelId, promptTokens, completionTokens, UUID.randomUUID().toString(), "chat");
    }

    /** 显式 capability 构造（usageId 自动生成）。 */
    public TokenUsageEvent(
            Long userId,
            Long modelId,
            long promptTokens,
            long completionTokens,
            String capability) {
        this(
                userId,
                modelId,
                promptTokens,
                completionTokens,
                UUID.randomUUID().toString(),
                capability);
    }

    public long totalTokens() {
        return promptTokens + completionTokens;
    }
}
