package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.model;

import java.util.Objects;
import java.util.function.Function;

import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.compiler.BoundedAgentCache;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.middleware.FunctionContractPromptMiddleware;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.Toolkit;

/**
 * L0 单例 {@code ReActAgent} 按 {@link AiModel#getModelId()} 分桶缓存的工厂。
 *
 * <p>{@link com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter} 是 per-call 路由，而
 * {@code ReActAgent.builder().model(...)} 是构造期固定参数，两者不能直接合一（依
 * ADR-007）：同一模型的多次调用复用同一实例，不同模型各自持有独立实例。 每个实例均为零工具（不设 {@code toolkit} 触发工具循环，传入空 {@link
 * Toolkit}）、无状态（不设 {@code stateStore}）、不设 {@code fallbackModel}（不静默切模型），基础 system prompt 留空由 {@link
 * FunctionContractPromptMiddleware} 按 per-call {@code RuntimeContext} 动态注入。
 *
 * <p>缓存复用 {@link BoundedAgentCache}（AAF-103 已验证的有界 LRU + 淘汰即 close 实现），不重新发明缓存逻辑。
 */
public final class L0ReActAgentFactory implements Function<AiModel, ReActAgent>, AutoCloseable {

    private final AgentScopeModelResolver modelResolver;
    private final BoundedAgentCache<String> cache;

    public L0ReActAgentFactory(AgentScopeModelResolver modelResolver, int cacheCapacity) {
        this.modelResolver = Objects.requireNonNull(modelResolver, "modelResolver 不能为空");
        this.cache = new BoundedAgentCache<>("l0-invoker", cacheCapacity);
    }

    @Override
    public ReActAgent apply(AiModel model) {
        Objects.requireNonNull(model, "model 不能为空");
        return cache.computeIfAbsent(model.getModelId(), ignored -> build(model));
    }

    private ReActAgent build(AiModel model) {
        var builder =
                ReActAgent.builder()
                        .name("l0-invoker-" + model.getModelId())
                        .sysPrompt("")
                        .model(modelResolver.resolve(model))
                        .toolkit(new Toolkit())
                        .middleware(new FunctionContractPromptMiddleware());
        // 不设 stateStore：L0 无状态，调用结束即弹出内存，不持久化
        // 不设 fallbackModel：CapabilityRouter 已解析出唯一模型，provider 层不静默切换（依 ADR-007）
        var generateOptions = generateOptions(model);
        if (generateOptions != null) {
            builder.generateOptions(generateOptions);
        }
        return builder.build();
    }

    /**
     * 仅思考型模型（{@code enableThinking=true}）才下发 {@code thinkingBudget}/{@code reasoningEffort}；
     * 非思考型模型不得被强制开启（AAF-106 #10601 完成标准），未配置字段时不下发对应参数。
     */
    private static GenerateOptions generateOptions(AiModel model) {
        if (!Boolean.TRUE.equals(model.getEnableThinking())) {
            return null;
        }
        var thinkingBudget = model.getThinkingBudget();
        var reasoningEffort = model.getReasoningEffort();
        if (thinkingBudget == null && (reasoningEffort == null || reasoningEffort.isBlank())) {
            return null;
        }
        var optionsBuilder = GenerateOptions.builder();
        if (thinkingBudget != null) {
            optionsBuilder.thinkingBudget(thinkingBudget);
        }
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            optionsBuilder.reasoningEffort(reasoningEffort);
        }
        return optionsBuilder.build();
    }

    @Override
    public void close() {
        cache.closeAll();
    }
}
