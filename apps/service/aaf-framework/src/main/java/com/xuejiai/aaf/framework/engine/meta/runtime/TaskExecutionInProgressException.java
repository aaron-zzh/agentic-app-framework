package com.xuejiai.aaf.framework.engine.meta.runtime;

/** 任务仍由其他执行持有或当前执行已失去租约；调用方应保留消息等待后续重试。 */
public class TaskExecutionInProgressException extends RuntimeException {

    public TaskExecutionInProgressException(String message) {
        super(message);
    }

    public TaskExecutionInProgressException(String message, Throwable cause) {
        super(message, cause);
    }
}
