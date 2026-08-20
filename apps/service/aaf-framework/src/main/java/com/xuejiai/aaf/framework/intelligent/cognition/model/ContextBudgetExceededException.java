package com.xuejiai.aaf.framework.intelligent.cognition.model;

/** Harness 输入无法在不破坏关键内容的前提下满足硬预算。 */
public final class ContextBudgetExceededException extends IllegalStateException {

    public static final String CODE = "CONTEXT_BUDGET_EXCEEDED";

    public ContextBudgetExceededException(String message) {
        super(CODE + ": " + message);
    }

    public ContextBudgetExceededException(String message, Throwable cause) {
        super(CODE + ": " + message, cause);
    }
}
