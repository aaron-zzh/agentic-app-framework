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
import com.xuejiai.aaf.framework.intelligent.agent.context.AgentRunContextHolder;
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
 *
 * <p>capability 当前固定为 {@code "agentscope"}，未做 chat / vision 子拆分： AgentScope SDK 的 ContentBlock
 * 体系当前无稳定的图像块判别 API，强行用类名反射兜底成本高、收益低。 待 SDK 暴露稳定的 vision 检测能力或本链路明确出现多模态规模化使用时再补。
 *
 * <p>modelId 当前传 {@code null}：AgentScope 暴露的 PostCallEvent / Agent 接口不直接给本次调用的 model 主键， 反查需要全局
 * Map<agentName, modelId>，跨 Agent 实例时易冲突。落库表已放开 model_id NOT NULL 约束（v11 迁移）。
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
                Long conversationId = resolveConversationId();
                var usageId = UUID.randomUUID().toString();
                meteringService.record(
                        userId,
                        (Long) null,
                        conversationId,
                        usage.getInputTokens(),
                        usage.getOutputTokens(),
                        usageId);
                settle(
                        userId,
                        usage.getInputTokens(),
                        usage.getOutputTokens(),
                        conversationId,
                        usageId);
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

    private void settle(
            Long userId, long inputTokens, long outputTokens, Long conversationId, String usageId) {
        var guard = creditGuard.getIfAvailable();
        if (guard == null) return;
        log.info(
                "AgentScope 计费结算: userId={}, conversationId={}, inputTokens={},"
                        + " outputTokens={}, usageId={}",
                userId,
                conversationId,
                inputTokens,
                outputTokens,
                usageId);
        AiUsage usage =
                new AiUsage() {
                    @Override
                    public Map<String, Object> standardUsage() {
                        return Map.of("inputTokens", inputTokens, "outputTokens", outputTokens);
                    }
                };
        guard.settleByUsage(userId, null, usage, "agentscope", null);
    }

    /** 从执行上下文取 conversationId（字符串），转为 Long。失败返回 null。 */
    private static Long resolveConversationId() {
        var ctx = AgentRunContextHolder.current().orElse(null);
        if (ctx == null || ctx.conversationId() == null) return null;
        try {
            return Long.valueOf(ctx.conversationId());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public int priority() {
        return 200; // 低优先级，不影响主流程
    }
}
