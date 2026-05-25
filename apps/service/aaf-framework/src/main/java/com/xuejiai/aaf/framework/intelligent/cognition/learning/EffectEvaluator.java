package com.xuejiai.aaf.framework.intelligent.cognition.learning;

/**
 * 效果评估器——对比预期目标与实际结果，产出效果评分。
 *
 * <p>对齐设计图"Learning 横切反哺通道"中的 EffectEvaluator 节点。
 * P2 占位：后续接入 LLM 评估或规则评估。
 */
public interface EffectEvaluator {

    /** 评估结果 */
    record EvalResult(double score, String summary) {
        /** 是否值得蒸馏（分数超过阈值） */
        public boolean worthDistilling() {
            return score >= 0.7 || score <= 0.3; // 高成功或高失败都值得学习
        }
    }

    /**
     * 评估执行效果。
     *
     * @param trajectory 执行轨迹
     * @return 评估结果
     */
    EvalResult evaluate(TrajectoryCollector.Trajectory trajectory);
}
