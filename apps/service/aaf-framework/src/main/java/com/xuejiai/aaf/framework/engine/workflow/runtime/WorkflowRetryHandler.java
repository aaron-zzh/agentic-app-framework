package com.xuejiai.aaf.framework.engine.workflow.runtime;

import java.util.Set;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** 工作流重试处理器——支持配置最大重试次数和退避策略。 */
@Slf4j
@Component
public class WorkflowRetryHandler {

    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_BACKOFF_MS = 1000;

    /** 可重试的异常类型 */
    private static final Set<Class<? extends Exception>> RETRYABLE =
            Set.of(java.io.IOException.class, java.util.concurrent.TimeoutException.class);

    /**
     * 判断是否应该重试。
     *
     * @param exception 异常
     * @param currentAttempt 当前尝试次数（从1开始）
     * @param maxRetries 最大重试次数
     * @return 是否应重试
     */
    public boolean shouldRetry(Exception exception, int currentAttempt, int maxRetries) {
        if (currentAttempt >= maxRetries) {
            return false;
        }
        return RETRYABLE.stream().anyMatch(cls -> cls.isInstance(exception));
    }

    /** 带重试执行——使用默认配置。 */
    public <T> T executeWithRetry(Supplier<T> action) {
        return executeWithRetry(action, DEFAULT_MAX_RETRIES, DEFAULT_BACKOFF_MS);
    }

    /**
     * 带重试执行——支持自定义最大重试次数和退避时间。
     *
     * @param action 待执行操作
     * @param maxRetries 最大重试次数
     * @param backoffMs 退避基础时间（毫秒），每次重试翻倍
     * @return 执行结果
     */
    public <T> T executeWithRetry(Supplier<T> action, int maxRetries, long backoffMs) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return action.get();
            } catch (Exception e) {
                lastException = e;
                if (!shouldRetry(e, attempt, maxRetries)) {
                    break;
                }
                var sleepMs = backoffMs * (1L << (attempt - 1));
                log.warn("工作流节点执行失败，第 {} 次重试，退避 {}ms", attempt, sleepMs, e);
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new RuntimeException("重试耗尽", lastException);
    }
}
