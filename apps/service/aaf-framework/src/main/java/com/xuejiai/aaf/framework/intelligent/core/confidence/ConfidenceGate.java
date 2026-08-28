package com.xuejiai.aaf.framework.intelligent.core.confidence;

/**
 * 置信度 × 可验证性 二维门控。
 *
 * <p>置信度先决定主导权，可验证性只决定自动校验与审查方式，<b>不得把低置信「升级」为自动执行</b>。
 *
 * <pre>
 * 置信度        可验证                        不可验证
 * &gt; 0.9        自动执行 → 自动验证             无副作用或可撤销且已获权时执行 → 异步审查；否则同步确认
 * 0.7..0.9     展示计划等待确认 → 执行并验证    展示计划、假设与风险等待确认 → 批准后执行并异步审查
 * &lt; 0.7        暂停，转人工                    暂停，转人工
 * </pre>
 *
 * <p>统一边界：<b>严格大于 0.9 才有资格自动执行；0.9 与 0.7 均落在确认区间</b>。不可逆动作无论置信度与可验证性如何都必须同步确认。
 *
 * <p>完整契约见 {@code docs/design/framework/intelligent/action-governance.md} 的「置信度门控」。
 */
public interface ConfidenceGate {

    /** 自动执行资格下界，严格大于该值才可能自动执行。 */
    double AUTO_EXECUTE_ABOVE = 0.9;

    /** 人工接管上界，低于该值一律转人工。 */
    double HUMAN_TAKEOVER_BELOW = 0.7;

    /** 门控输入 */
    record GateInput(
            /** 置信度（0.0-1.0） */
            double confidence,
            /** 任务结果是否可自动验证 */
            boolean verifiable,
            /** 动作是否不可逆或无可靠补偿；不可逆一律同步确认 */
            boolean irreversible,
            /** 动作是否无副作用，或可撤销且已获权 */
            boolean safeToStage,
            /** 任务类型标识（可为 null） */
            String taskType) {

        public GateInput {
            if (confidence < 0.0 || confidence > 1.0 || Double.isNaN(confidence)) {
                throw new IllegalArgumentException("置信度必须落在 0.0..1.0，缺失或越界一律 fail-closed");
            }
        }

        /** 只读、可逆且可暂存的默认动作画像。 */
        public GateInput(double confidence, boolean verifiable) {
            this(confidence, verifiable, false, true, null);
        }

        public GateInput(double confidence, boolean verifiable, String taskType) {
            this(confidence, verifiable, false, true, taskType);
        }
    }

    /** 门控执行动作 */
    enum Action {
        /** 高置信 + 可验证：自动执行，自动验证 */
        AUTO_EXECUTE,
        /** 高置信 + 不可验证且可暂存：执行 + 决策日志 + 异步审查 */
        EXECUTE_WITH_AUDIT,
        /** 中置信区间，或高置信但不可暂存：展示计划，等待人工确认后执行 */
        CONFIRM_BEFORE_EXECUTE,
        /** 低置信，或不可逆且无法自动证明：暂停，转人工决策 */
        PAUSE_FOR_HUMAN
    }

    /** 门控决策结果 */
    record GateDecision(Action action, String message, boolean requiresAuditLog) {}

    /** 评估执行策略。 */
    GateDecision evaluate(GateInput input);
}
