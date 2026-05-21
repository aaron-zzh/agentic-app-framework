/**
 * Token 用量事件。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.ai.chat;

/** 每次 LLM 调用后发布的 Token 用量事件，供计量和配额控制使用。 */
public record TokenUsageEvent(Long userId, String model, long promptTokens, long completionTokens) {

    public long totalTokens() {
        return promptTokens + completionTokens;
    }
}
