package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.time.Duration;
import java.util.Objects;

/** 单次 Agent 回合的确定性执行边界。 */
public record ExecutionPolicy(int maxIterations, int maxModelRetries, Duration timeout) {

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
    }
}
