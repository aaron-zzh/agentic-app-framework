package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.core.confidence.ConfidenceGate;
import com.xuejiai.aaf.framework.intelligent.core.confidence.ConfidenceGate.Action;
import com.xuejiai.aaf.framework.intelligent.core.confidence.ConfidenceGate.GateInput;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.message.ToolUseBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 置信度门控 Hook——PostReasoningEvent 时评估工具调用置信度，
 * 低置信度且不可验证时暂停 Agent 等待人工确认。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AafConfidenceHook implements Hook {

    private final ConfidenceGate confidenceGate;

    /** 含工具调用的默认置信度（需门控评估） */
    private static final double TOOL_CALL_CONFIDENCE = 0.75;

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PostReasoningEvent postReasoning) {
            return evaluateConfidence(postReasoning).map(e -> event);
        }
        return Mono.just(event);
    }

    @Override
    public int priority() {
        return 80; // 高于普通业务 Hook，低于安全 Hook
    }

    private Mono<PostReasoningEvent> evaluateConfidence(PostReasoningEvent event) {
        var msg = event.getReasoningMessage();
        if (msg == null || msg.getContent() == null) {
            return Mono.just(event);
        }

        // 检查是否包含工具调用
        boolean hasToolUse = msg.getContent().stream()
                .anyMatch(block -> block instanceof ToolUseBlock);
        if (!hasToolUse) {
            return Mono.just(event);
        }

        // 评估门控（工具调用默认视为可验证）
        var input = new GateInput(TOOL_CALL_CONFIDENCE, true);
        var decision = confidenceGate.evaluate(input);

        if (decision.action() == Action.PAUSE_FOR_HUMAN) {
            log.info("置信度门控：暂停 Agent，等待人工确认");
            event.stopAgent();
        }

        return Mono.just(event);
    }
}
