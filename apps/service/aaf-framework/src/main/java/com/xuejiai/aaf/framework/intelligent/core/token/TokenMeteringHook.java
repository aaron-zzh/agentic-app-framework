/**
 * AgentScope Token 计量 Hook。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.core.token;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.security.OperatorContext;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostCallEvent;
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

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PostCallEvent postCall) {
            var msg = postCall.getFinalMessage();
            if (msg != null && msg.getChatUsage() != null) {
                var usage = msg.getChatUsage();
                var userId = operatorContext.currentUserId().orElse(null);
                var modelName = "agentscope";
                meteringService.record(
                        userId, modelName, null, usage.getInputTokens(), usage.getOutputTokens());
            }
        }
        return Mono.just(event);
    }

    @Override
    public int priority() {
        return 200; // 低优先级，不影响主流程
    }
}
