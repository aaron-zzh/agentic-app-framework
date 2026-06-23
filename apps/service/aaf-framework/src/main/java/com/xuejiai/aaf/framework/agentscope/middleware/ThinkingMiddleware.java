/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.middleware;

import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xuejiai.aaf.framework.agentscope.runtime.AafContextHolder;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.GenerateOptions;
import reactor.core.publisher.Flux;

/**
 * 按对话（per-thread）动态开启思考模式的中间件。
 *
 * <p>读取 {@link AafContextHolder#enableThinking()}：为 true 时在 {@code onModelCall} 中把 {@link
 * GenerateOptions#getThinkingBudget()} 注入本次调用，实现对话粒度开关。
 *
 * <p>支持两类模型：
 *
 * <ul>
 *   <li>DashScope / OpenAI-compat 思考型模型 — 通过 {@code thinkingBudget} 开启
 *   <li>OpenAI o1/o3 系列 — 通过 {@code reasoningEffort=high} 开启（当 thinkingBudget=0 时触发）
 * </ul>
 */
public class ThinkingMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(ThinkingMiddleware.class);

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent,
            RuntimeContext ctx,
            ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next) {

        if (!AafContextHolder.enableThinking()) {
            return next.apply(input);
        }

        int budget = AafContextHolder.thinkingBudget();
        GenerateOptions enriched;

        if (budget > 0) {
            // DashScope / Anthropic extended thinking：注入 thinkingBudget
            enriched =
                    GenerateOptions.mergeOptions(
                            GenerateOptions.builder().thinkingBudget(budget).build(),
                            input.options());
        } else {
            // OpenAI o1/o3：注入 reasoningEffort=high
            enriched =
                    GenerateOptions.mergeOptions(
                            GenerateOptions.builder().reasoningEffort("high").build(),
                            input.options());
        }

        log.debug(
                "[ThinkingMiddleware] 启用思考模式 threadId={} budget={}",
                AafContextHolder.threadId(),
                budget);

        return next.apply(
                new ModelCallInput(input.messages(), input.tools(), enriched, input.model()));
    }
}
