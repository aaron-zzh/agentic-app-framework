package com.xuejiai.aaf.framework.task;

/** 通用异步任务的逻辑生命周期状态。 */
public enum AsyncTaskStatus {
    PENDING,
    QUEUED,
    RUNNING,
    RETRY_WAIT,
    SUCCEEDED,
    FAILED
}
