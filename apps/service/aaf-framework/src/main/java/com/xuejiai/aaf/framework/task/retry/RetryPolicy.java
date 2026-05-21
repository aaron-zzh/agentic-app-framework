package com.xuejiai.aaf.framework.task.retry;

import java.time.Duration;

/**
 * 重试策略。
 *
 * @param maxRetries 最大重试次数
 * @param initialDelay 初始延迟
 * @param multiplier 退避倍数
 * @param maxDelay 最大延迟
 */
public record RetryPolicy(
        int maxRetries, Duration initialDelay, double multiplier, Duration maxDelay) {

    public static final RetryPolicy DEFAULT =
            new RetryPolicy(3, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(60));

    /** 计算第 n 次重试的延迟 */
    public Duration delayForAttempt(int attempt) {
        var delayMs = (long) (initialDelay.toMillis() * Math.pow(multiplier, attempt - 1));
        return Duration.ofMillis(Math.min(delayMs, maxDelay.toMillis()));
    }
}
