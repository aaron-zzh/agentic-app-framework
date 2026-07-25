package com.xuejiai.aaf.framework.engine.task.agent;

/** 智能体任务单次执行结果。 */
public record AgentTaskOutcome(Outcome outcome, String detail) {

    public enum Outcome {
        COMPLETED,
        FAILED_RETRYABLE,
        FAILED_TERMINAL,
        NOT_TERMINAL
    }

    public static AgentTaskOutcome completed() {
        return new AgentTaskOutcome(Outcome.COMPLETED, null);
    }

    public static AgentTaskOutcome retryable(String detail) {
        return new AgentTaskOutcome(Outcome.FAILED_RETRYABLE, detail);
    }

    public static AgentTaskOutcome terminal(String detail) {
        return new AgentTaskOutcome(Outcome.FAILED_TERMINAL, detail);
    }

    public static AgentTaskOutcome pending(String detail) {
        return new AgentTaskOutcome(Outcome.NOT_TERMINAL, detail);
    }
}
