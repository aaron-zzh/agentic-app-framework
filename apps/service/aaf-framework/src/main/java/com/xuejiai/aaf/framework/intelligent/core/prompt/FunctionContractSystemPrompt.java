package com.xuejiai.aaf.framework.intelligent.core.prompt;

import java.util.Objects;

/**
 * L0 单例 {@code ReActAgent} 的 per-call system prompt 载体。
 *
 * <p>L0 场景每次调用的 Function Contract 各不相同（意图理解、参数抽取、情感感知等 system prompt 互不相同），但复用同一个零工具 {@code
 * ReActAgent} 单例，不按调用方分别 build 实例。该 record 通过 {@code RuntimeContext} 类型化属性传递给 {@link
 * com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.middleware.FunctionContractPromptMiddleware}，由其在
 * {@code onSystemPrompt} 钩子内注入为本次调用实际生效的 system prompt。
 */
public record FunctionContractSystemPrompt(String content) {
    public FunctionContractSystemPrompt {
        Objects.requireNonNull(content, "content 不能为空");
    }
}
