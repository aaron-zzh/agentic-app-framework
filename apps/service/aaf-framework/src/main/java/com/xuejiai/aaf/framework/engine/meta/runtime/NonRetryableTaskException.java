package com.xuejiai.aaf.framework.engine.meta.runtime;

/** 任务输入或输出违反确定性契约，重试不能改变结果。 */
public class NonRetryableTaskException extends IllegalArgumentException {

    public NonRetryableTaskException(String message) {
        super(message);
    }

    public NonRetryableTaskException(String message, Throwable cause) {
        super(message, cause);
    }
}
