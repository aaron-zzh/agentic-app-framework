package com.xuejiai.aaf.framework.intelligent.assistant.model;

/**
 * Coordinator 当前可执行的任务分解预算。
 *
 * <p>领域绝对硬上限保留为 8；普通动态计划默认使用 5。固定 Team 已在发布时冻结 roster，生效预算必须至少覆盖该 roster， 但不能突破硬上限。Harness ReAct
 * 与模型重试继续由 ExecutionPolicy 管理。
 */
public record DecompositionBudget(
        int maxChildrenPerPlan, int maxParallelAgents, int maxOuterIterations) {
    public static final int HARD_LIMIT = 8;
    public static final int DEFAULT_LIMIT = 5;

    public DecompositionBudget {
        requireLimit(maxChildrenPerPlan, "maxChildrenPerPlan");
        requireLimit(maxParallelAgents, "maxParallelAgents");
        requireLimit(maxOuterIterations, "maxOuterIterations");
    }

    public static DecompositionBudget defaults() {
        return new DecompositionBudget(DEFAULT_LIMIT, DEFAULT_LIMIT, DEFAULT_LIMIT);
    }

    /** 固定 Team 的有效上限至少覆盖已冻结成员，普通动态计划不得调用此方法扩容。 */
    public DecompositionBudget effectiveForFixedTeam(int frozenWorkerCount) {
        requireLimit(frozenWorkerCount, "frozenWorkerCount");
        return new DecompositionBudget(
                Math.max(maxChildrenPerPlan, frozenWorkerCount),
                Math.max(maxParallelAgents, frozenWorkerCount),
                maxOuterIterations);
    }

    /** 超限直接拒绝，不静默缩减模型提出的计划。 */
    public void requireWithin(int childCount, int parallelism, int outerIterations) {
        if (childCount < 1 || childCount > maxChildrenPerPlan) {
            throw new IllegalArgumentException(
                    "协调计划子任务数超出分解预算: " + childCount + "/" + maxChildrenPerPlan);
        }
        if (parallelism < 1 || parallelism > maxParallelAgents) {
            throw new IllegalArgumentException(
                    "协调计划并行度超出分解预算: " + parallelism + "/" + maxParallelAgents);
        }
        if (outerIterations < 1 || outerIterations > maxOuterIterations) {
            throw new IllegalArgumentException(
                    "协调计划外层迭代超出分解预算: " + outerIterations + "/" + maxOuterIterations);
        }
    }

    private static void requireLimit(int value, String field) {
        if (value < 1 || value > HARD_LIMIT) {
            throw new IllegalArgumentException(field + " 必须在 1.." + HARD_LIMIT);
        }
    }
}
