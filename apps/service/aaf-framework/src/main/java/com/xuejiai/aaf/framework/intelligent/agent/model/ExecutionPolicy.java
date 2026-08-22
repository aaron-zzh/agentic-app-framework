package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.time.Duration;
import java.util.Objects;

/**
 * 单次 Agent 回合的确定性执行边界。
 *
 * <p>{@code contextWindow} 是本次执行所用模型的上下文窗口 token 数，唯一来源是 {@code
 * aaf.ai.context.default-context-window} 配置，不在此处保留第二份默认值。它让调用前长度预检能算出占用率，而不只是绝对长度。
 */
public record ExecutionPolicy(
        int maxIterations, int maxModelRetries, Duration timeout, int contextWindow) {

    public ExecutionPolicy {
        if (maxIterations < 1) {
            throw new IllegalArgumentException("maxIterations 必须大于 0");
        }
        if (maxModelRetries < 0) {
            throw new IllegalArgumentException("maxModelRetries 不能小于 0");
        }
        Objects.requireNonNull(timeout, "timeout 不能为空");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout 必须大于 0");
        }
        if (contextWindow < 1) {
            throw new IllegalArgumentException("contextWindow 必须大于 0");
        }
    }
}
