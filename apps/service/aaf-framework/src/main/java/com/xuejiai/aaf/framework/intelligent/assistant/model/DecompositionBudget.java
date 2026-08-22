package com.xuejiai.aaf.framework.intelligent.assistant.model;

/**
 * Coordinator 当前可执行的任务分解预算。
 *
 * <p>预算分两个维度：单个计划的形状上限（子任务数、并行度、外层迭代数）和整个任务累计的 Executor 运行上限。只校验形状不校验累计时，外层迭代会把形状上限逐轮相乘，
 * 因此累计上限必须独立存在。
 *
 * <p>形状硬上限保留为 8，普通动态计划默认 5；累计硬上限为 24，默认 10。固定 Team 已在发布时冻结 roster，生效预算必须至少覆盖该 roster，但不能突破硬上限。
 * Harness ReAct 与模型重试继续由 ExecutionPolicy 管理。
 */
public record DecompositionBudget(
        int maxChildrenPerPlan,
        int maxParallelAgents,
        int maxOuterIterations,
        int maxTotalExecutorRuns) {
    public static final int HARD_LIMIT = 8;
    public static final int DEFAULT_LIMIT = 5;
    public static final int TOTAL_HARD_LIMIT = 24;
    public static final int DEFAULT_TOTAL_LIMIT = 10;

    public DecompositionBudget {
        requireShapeLimit(maxChildrenPerPlan, "maxChildrenPerPlan");
        requireShapeLimit(maxParallelAgents, "maxParallelAgents");
        requireShapeLimit(maxOuterIterations, "maxOuterIterations");
        requireTotalLimit(maxTotalExecutorRuns);
        if (maxTotalExecutorRuns < maxChildrenPerPlan) {
            throw new IllegalArgumentException("maxTotalExecutorRuns 不能小于 maxChildrenPerPlan");
        }
    }

    public static DecompositionBudget defaults() {
        return new DecompositionBudget(
                DEFAULT_LIMIT, DEFAULT_LIMIT, DEFAULT_LIMIT, DEFAULT_TOTAL_LIMIT);
    }

    /** 固定 Team 的有效上限至少覆盖已冻结成员，普通动态计划不得调用此方法扩容。 */
    public DecompositionBudget effectiveForFixedTeam(int frozenWorkerCount) {
        requireShapeLimit(frozenWorkerCount, "frozenWorkerCount");
        return new DecompositionBudget(
                Math.max(maxChildrenPerPlan, frozenWorkerCount),
                Math.max(maxParallelAgents, frozenWorkerCount),
                maxOuterIterations,
                Math.max(maxTotalExecutorRuns, frozenWorkerCount));
    }

    /** 校验单个计划的形状；超限直接拒绝，不静默缩减模型提出的计划。 */
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

    /** 校验整个任务累计的 Executor 运行数；跨迭代重规划必须计入已冻结的历史子任务。 */
    public void requireCumulativeWithin(int totalExecutorRuns) {
        if (totalExecutorRuns < 1 || totalExecutorRuns > maxTotalExecutorRuns) {
            throw new IllegalArgumentException(
                    "协调计划累计 Executor 运行数超出分解预算: " + totalExecutorRuns + "/" + maxTotalExecutorRuns);
        }
    }

    private static void requireShapeLimit(int value, String field) {
        if (value < 1 || value > HARD_LIMIT) {
            throw new IllegalArgumentException(field + " 必须在 1.." + HARD_LIMIT);
        }
    }

    private static void requireTotalLimit(int value) {
        if (value < 1 || value > TOTAL_HARD_LIMIT) {
            throw new IllegalArgumentException("maxTotalExecutorRuns 必须在 1.." + TOTAL_HARD_LIMIT);
        }
    }
}
