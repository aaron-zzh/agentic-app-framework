package com.xuejiai.aaf.framework.security.authorization;

/** 已编译策略与运行时事实不一致或无法求值。 */
public final class PolicyEvaluationException extends RuntimeException {

    public PolicyEvaluationException(String message) {
        super(message);
    }
}
