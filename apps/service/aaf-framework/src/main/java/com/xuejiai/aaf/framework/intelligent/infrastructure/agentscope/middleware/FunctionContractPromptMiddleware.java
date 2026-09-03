package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.middleware;

import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.core.prompt.FunctionContractSystemPrompt;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.middleware.MiddlewareBase;
import reactor.core.publisher.Mono;

/**
 * L0 单例 {@code ReActAgent} 的 per-call system prompt 注入点。
 *
 * <p>L0（非自主、请求级、无人格无记忆）复用同一个零工具 {@code ReActAgent} 单例服务全部 Function Contract，因此 builder 期固定的基础
 * {@code sysPrompt} 留空，实际内容由本 middleware 在每次调用时从 {@link RuntimeContext#get(Class)} 取出的 {@link
 * FunctionContractSystemPrompt} 决定——参照官方 {@code PlanModeMiddleware.onSystemPrompt} 的用法模式（按 {@code
 * RuntimeContext} 动态改写 prompt，不新建多个 Agent 实例）。
 *
 * <p>未携带 {@link FunctionContractSystemPrompt} 的调用视为配置错误，直接失败而非静默回退到空 prompt——L0 调用必须显式声明 Function
 * Contract，不允许无契约调用模型。
 */
public final class FunctionContractPromptMiddleware implements MiddlewareBase {

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext ctx, String currentPrompt) {
        var contract = ctx == null ? null : ctx.get(FunctionContractSystemPrompt.class);
        if (contract == null) {
            return Mono.error(
                    new IllegalStateException("L0 调用缺少 FunctionContractSystemPrompt，禁止无契约调用模型"));
        }
        return Mono.just(Objects.requireNonNull(contract.content(), "content 不能为空"));
    }
}
