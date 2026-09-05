package com.xuejiai.aaf.module.ai.aigc.execution.api;

/** ExecutionRun 正式状态。持久化与 API 均使用枚举名的大写值。 */
public enum AigcExecutionRunStatus {
    PENDING_BIND,
    PENDING,
    RUNNING,
    SUCCEEDED,
    PARTIALLY_SUCCEEDED,
    FAILED,
    CANCELED;

    public boolean isActive() {
        return this == PENDING_BIND || this == PENDING || this == RUNNING;
    }

    public boolean isTerminal() {
        return !isActive();
    }

    public boolean isRetryable() {
        return this == FAILED || this == CANCELED || this == PARTIALLY_SUCCEEDED;
    }
}
