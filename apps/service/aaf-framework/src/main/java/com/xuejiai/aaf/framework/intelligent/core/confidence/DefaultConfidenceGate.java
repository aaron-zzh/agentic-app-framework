package com.xuejiai.aaf.framework.intelligent.core.confidence;

import org.springframework.stereotype.Component;

/**
 * 默认三段置信度门控实现。
 *
 * <p>严格大于 {@link #AUTO_EXECUTE_ABOVE} 才有资格自动执行；{@code 0.7..0.9} 为确认区间；低于 {@link
 * #HUMAN_TAKEOVER_BELOW} 一律转人工。不可逆动作无论置信度与可验证性如何都必须同步确认。
 *
 * <p>不实现「低置信但可验证即可先执行回滚」的越级规则——该规则已被动作治理设计否决。
 */
@Component
public class DefaultConfidenceGate implements ConfidenceGate {

    @Override
    public GateDecision evaluate(GateInput input) {
        // 不可逆且无可靠补偿：任何置信度都不得自动放行
        if (input.irreversible()) {
            return new GateDecision(Action.CONFIRM_BEFORE_EXECUTE, "动作不可逆或无可靠补偿，执行前必须人工确认。", true);
        }

        // 低置信区间：暂停转人工，可验证性不构成越级理由
        if (input.confidence() < HUMAN_TAKEOVER_BELOW) {
            return new GateDecision(
                    Action.PAUSE_FOR_HUMAN,
                    input.verifiable() ? "置信度低于门槛，已暂停并说明不确定性与验证方案，转人工决策。" : "置信度低于门槛且无法自动验证，转人工决策。",
                    true);
        }

        // 确认区间：0.7 ≤ c ≤ 0.9，展示计划等待确认
        if (input.confidence() <= AUTO_EXECUTE_ABOVE) {
            return new GateDecision(
                    Action.CONFIRM_BEFORE_EXECUTE,
                    input.verifiable()
                            ? "置信度处于确认区间，展示计划并等待确认；批准后执行并自动验证。"
                            : "置信度处于确认区间且无法自动验证，展示计划、假设与风险并等待确认。",
                    true);
        }

        // 高置信 + 可验证：自动执行并自动验证
        if (input.verifiable()) {
            return new GateDecision(Action.AUTO_EXECUTE, null, false);
        }

        // 高置信 + 不可验证：仅无副作用或可撤销且已获权时才可执行后异步审查
        if (input.safeToStage()) {
            return new GateDecision(Action.EXECUTE_WITH_AUDIT, "结果无法自动验证，已记录决策摘要并进入异步审查。", true);
        }
        return new GateDecision(
                Action.CONFIRM_BEFORE_EXECUTE, "结果无法自动验证且副作用不可撤销或未获权，执行前需人工确认。", true);
    }
}
