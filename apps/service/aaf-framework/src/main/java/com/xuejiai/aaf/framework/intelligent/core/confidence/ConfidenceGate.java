package com.xuejiai.aaf.framework.intelligent.core.confidence;

/**
 * 置信度 × 可验证性 二维门控。
 *
 * <p>对齐设计图四象限决策矩阵：
 *
 * <pre>
 *                  低置信度              高置信度
 * 可验证     执行+验证+失败回滚      自动执行+自动验证
 * 不可验证   暂停+转人工决策         执行+决策日志+异步审查
 * </pre>
 *
 * <h3>集成点</h3>
 *
 * <ul>
 *   <li><b>Agent 执行前</b>：{@code AgentDispatcher} 根据意图理解置信度 + 任务可验证性决定策略
 *   <li><b>Agent 执行中</b>：{@code CognitiveCycleExecutor} 评估阶段检查置信度是否跌破阈值
 *   <li><b>Agent 执行后</b>：{@code ResultAggregator} 对最终结果标注置信度，决定返回策略
 *   <li><b>元引擎路由前</b>：{@code ExecutionDispatcher} 调用门控决定是否自动执行
 * </ul>
 */
public interface ConfidenceGate {

    /** 门控输入 */
    record GateInput(
            /** 置信度（0.0-1.0） */
            double confidence,
            /** 任务结果是否可自动验证 */
            boolean verifiable,
            /** 任务类型标识（可为 null） */
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
    record GateDecision(Action action, String message, boolean requiresAuditLog) {}

    /** 评估执行策略。 */
    GateDecision evaluate(GateInput input);
}
