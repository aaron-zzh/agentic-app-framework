package com.xuejiai.aaf.framework.security.authorization;

/** 策略 DSL 不满足语法、类型或资源限制。 */
public final class PolicyCompilationException extends RuntimeException {

    public PolicyCompilationException(String message) {
        super(message);
    }

    public PolicyCompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
