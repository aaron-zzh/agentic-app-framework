package com.xuejiai.aaf.framework.intelligent.assistant.model;

/** evaluator 对当前迭代轮次给出的严格结构化决策。 */
public record IterationEvaluation(Decision decision, String reason) {

    public IterationEvaluation {
        if (decision == null) {
            throw new IllegalArgumentException("decision 不能为空");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason 不能为空白");
        }
        reason = reason.trim();
    }

    public enum Decision {
        CONTINUE,
        COMPLETE,
        BLOCKED
    }
}
