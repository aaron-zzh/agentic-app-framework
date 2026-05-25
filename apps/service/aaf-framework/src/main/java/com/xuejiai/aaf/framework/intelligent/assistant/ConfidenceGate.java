package com.xuejiai.aaf.framework.intelligent.assistant;

/**
 * 置信度 × 可验证性 二维门控。
 *
 * <p>对齐设计图四象限决策矩阵：
 * <pre>
 *                  低置信度              高置信度
 * 可验证     执行+验证+失败回滚      自动执行+自动验证
 * 不可验证   暂停+转人工决策         执行+决策日志+异步审查
 * </pre>
 *
 * <h3>使用说明</h3>
 * <ol>
 *   <li>调用方构造 {@link GateInput}，提供置信度分数和可验证性标记</li>
 *   <li>调用 {@link #evaluate(GateInput)} 获取 {@link GateDecision}</li>
 *   <li>根据 decision 的 {@link Action} 决定后续行为：
 *       <ul>
 *         <li>{@code AUTO_EXECUTE} — 直接执行，结果自动验证</li>
 *         <li>{@code EXECUTE_AND_VERIFY} — 执行后必须验证，失败自动回滚</li>
 *         <li>{@code EXECUTE_WITH_AUDIT} — 执行但记录决策日志，异步人工审查</li>
 *         <li>{@code PAUSE_FOR_HUMAN} — 不执行，暂停等待人工决策</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h3>集成点（后续补充调用）</h3>
 * <ul>
 *   <li><b>Agent 执行前</b>：{@code AgentDispatcher} 根据意图理解置信度 + 任务可验证性决定策略</li>
 *   <li><b>Agent 执行中</b>：{@code CognitiveCycleExecutor} 评估阶段检查置信度是否跌破阈值</li>
 *   <li><b>Agent 执行后</b>：{@code ResultAggregator} 对最终结果标注置信度，决定返回策略</li>
 * </ul>
 */
public interface ConfidenceGate {

    /** 门控输入 */
    record GateInput(
            /** 置信度（0.0-1.0） */
            double confidence,
            /** 任务结果是否可自动验证（如：有测试用例、有预期输出可比对） */
            boolean verifiable,
            /** 任务类型标识（用于细粒度策略，可为 null） */
            String taskType) {

        public GateInput(double confidence, boolean verifiable) {
            this(confidence, verifiable, null);
        }
    }

    /** 门控执行动作 */
    enum Action {
        /** 高置信 + 可验证：自动执行，自动验证 */
        AUTO_EXECUTE,
        /** 低置信 + 可验证：执行 + 验证 + 失败回滚 */
        EXECUTE_AND_VERIFY,
        /** 高置信 + 不可验证：执行 + 决策日志 + 异步审查 */
        EXECUTE_WITH_AUDIT,
        /** 低置信 + 不可验证：暂停，转人工决策 */
        PAUSE_FOR_HUMAN
    }

    /** 门控决策结果 */
    record GateDecision(
            Action action,
            /** 给用户的提示信息（PAUSE_FOR_HUMAN 时必填） */
            String message,
            /** 是否需要记录决策日志 */
            boolean requiresAuditLog) {}

    /**
     * 评估执行策略。
     *
     * @param input 门控输入（置信度 + 可验证性）
     * @return 门控决策
     */
    GateDecision evaluate(GateInput input);
}
