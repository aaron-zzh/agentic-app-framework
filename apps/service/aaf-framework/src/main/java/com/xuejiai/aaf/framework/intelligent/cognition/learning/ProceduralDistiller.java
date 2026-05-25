package com.xuejiai.aaf.framework.intelligent.cognition.learning;

import java.util.List;

/**
 * 程序化记忆蒸馏器——从执行轨迹中提取可复用的模式/教训。
 *
 * <p>对齐设计图"Learning 横切反哺通道"中的 ProceduralDistiller 节点。
 * 蒸馏产出三类：成功模式、失败教训、对比分析。
 * P2 占位：后续由 LLM 驱动蒸馏。
 */
public interface ProceduralDistiller {

    /** 蒸馏产出 */
    record DistilledKnowledge(Type type, String content, List<String> tags) {}

    /** 蒸馏类型 */
    enum Type {
        /** 成功模式——可直接复用的执行策略 */
        SUCCESS_PATTERN,
        /** 失败教训——应避免的路径 */
        FAILURE_LESSON,
        /** 对比分析——同类任务不同策略的优劣 */
        COMPARATIVE_INSIGHT
    }

    /**
     * 从轨迹中蒸馏程序化知识。
     *
     * @param trajectory 执行轨迹
     * @param evalResult 效果评估结果
     * @return 蒸馏产出（可能为空列表）
     */
    List<DistilledKnowledge> distill(
            TrajectoryCollector.Trajectory trajectory, EffectEvaluator.EvalResult evalResult);
}
