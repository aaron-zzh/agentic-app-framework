package com.xuejiai.aaf.framework.intelligent.assistant;

import org.springframework.stereotype.Component;

/**
 * 默认二维门控实现——四象限决策。
 *
 * <pre>
 * confidence >= HIGH  && verifiable  → AUTO_EXECUTE
 * confidence >= HIGH  && !verifiable → EXECUTE_WITH_AUDIT
 * confidence < HIGH   && verifiable  → EXECUTE_AND_VERIFY
 * confidence < HIGH   && !verifiable → PAUSE_FOR_HUMAN
 * </pre>
 */
@Component
public class DefaultConfidenceGate implements ConfidenceGate {

    private static final double HIGH_THRESHOLD = 0.7;

    @Override
    public GateDecision evaluate(GateInput input) {
        boolean highConfidence = input.confidence() >= HIGH_THRESHOLD;

        if (highConfidence && input.verifiable()) {
            return new GateDecision(Action.AUTO_EXECUTE, null, false);
        }
        if (highConfidence) {
            return new GateDecision(Action.EXECUTE_WITH_AUDIT, null, true);
        }
        if (input.verifiable()) {
            return new GateDecision(
                    Action.EXECUTE_AND_VERIFY,
                    "置信度较低，将执行并自动验证，失败时回滚。",
                    true);
        }
        return new GateDecision(
                Action.PAUSE_FOR_HUMAN,
                "置信度较低且无法自动验证，需要人工确认后再执行。",
                true);
    }
}
