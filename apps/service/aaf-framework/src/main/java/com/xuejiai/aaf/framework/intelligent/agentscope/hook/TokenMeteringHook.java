/**
 * AgentScope Token 计量 Hook。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.agentscope.hook;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.core.AiUsage;
import com.xuejiai.aaf.framework.intelligent.core.token.TokenMeteringService;
import com.xuejiai.aaf.framework.security.OperatorContext;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * AgentScope Hook：在 Agent 调用完成后记录 Token 用量。 桥接 AgentScope 的 getChatUsage() 与 AAF 的
 * TokenMeteringService。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenMeteringHook implements Hook {

    private final TokenMeteringService meteringService;
    private final OperatorContext operatorContext;
    private final ObjectProvider<AiCreditGuard> creditGuard;

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreReasoningEvent) {
            precheck();
        } else if (event instanceof PostCallEvent postCall) {
            var msg = postCall.getFinalMessage();
            if (msg != null && msg.getChatUsage() != null) {
                var usage = msg.getChatUsage();
                Long userId = operatorContext.currentOwnerId().orElse(null);
                if (userId == null) {
                    log.debug("TokenMeteringHook: userId 为空，跳过 Token 计量");
                    return Mono.just(event);
                }
                var usageId = UUID.randomUUID().toString();
                meteringService.record(
                        userId,
                        (Long) null,
                        null,
                        usage.getInputTokens(),
                        usage.getOutputTokens(),
                        usageId);
                settle(userId, usage.getInputTokens(), usage.getOutputTokens());
            }
        }
        return Mono.just(event);
    }

    private void precheck() {
        var guard = creditGuard.getIfAvailable();
        if (guard == null) return;
        Long userId = operatorContext.currentOwnerId().orElse(null);
        if (userId == null) return;
        guard.precheck(userId, "agentscope", AiCreditGuard.INESTIMABLE_COST);
    }

    private void settle(Long userId, long inputTokens, long outputTokens) {
        var guard = creditGuard.getIfAvailable();
        if (guard == null) return;
        AiUsage usage =
                new AiUsage() {
                    @Override
                    public Map<String, Object> standardUsage() {
                        return Map.of("inputTokens", inputTokens, "outputTokens", outputTokens);
                    }
                };
        guard.settleByUsage(userId, null, usage, "agentscope", null);
    }

    @Override
    public int priority() {
        return 200; // 低优先级，不影响主流程
    }
}
