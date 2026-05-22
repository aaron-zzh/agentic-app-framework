package com.xuejiai.aaf.framework.intelligent.core.model;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 辅助模型选择器，根据任务特征自动选择最合适的模型。
 *
 * <p>选择规则（按优先级）：
 * <ol>
 *   <li>含图片/视频 → 选 capabilities 含 VISION 的模型
 *   <li>需要推理 → 选推理模型（deepseek-reasoner / o1 / r1）
 *   <li>长文本（>32k tokens）→ 选长上下文模型
 *   <li>成本敏感 → 选同能力中 inputPricePerK 最低的模型
 *   <li>无特征匹配 → 返回 null（交给下一层决策）
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelSelector {

    private final AiModelRepository modelRepository;

    /**
     * 根据任务特征选择模型。
     *
     * @return modelId，无法决策时返回 null
     */
    public String select(CapabilityRoutingContext ctx) {
        if (ctx.taskFeatures() == null || ctx.taskFeatures().isEmpty()) return null;

        var features = ctx.taskFeatures();
        var capability = ctx.capability() != null ? ctx.capability() : CapabilityRoutingContext.CAP_CHAT;

        // 含图片/视频 → 需要 VISION 能力
        if (Boolean.TRUE.equals(features.get(CapabilityRoutingContext.FEATURE_HAS_IMAGE))
                || Boolean.TRUE.equals(features.get(CapabilityRoutingContext.FEATURE_HAS_VIDEO))) {
            return modelRepository.findByEnabledTrueOrderBySortOrder().stream()
                    .filter(m -> m.hasCapability("VISION"))
                    .map(AiModel::getModelId)
                    .findFirst()
                    .orElse(null);
        }

        // 需要推理 → 选推理模型
        if (Boolean.TRUE.equals(features.get(CapabilityRoutingContext.FEATURE_REASONING_REQUIRED))) {
            return modelRepository.findByEnabledTrueOrderBySortOrder().stream()
                    .filter(m -> m.hasCapability(capability))
                    .filter(m -> m.getModelName().contains("reasoner")
                            || m.getModelName().contains("o1")
                            || m.getModelName().contains("r1"))
                    .map(AiModel::getModelId)
                    .findFirst()
                    .orElse(null);
        }

        // 长文本 → 选大上下文窗口模型
        var inputLength = (Number) features.get(CapabilityRoutingContext.FEATURE_INPUT_LENGTH);
        if (inputLength != null && inputLength.longValue() > 32000) {
            return modelRepository.findByEnabledTrueOrderBySortOrder().stream()
                    .filter(m -> m.hasCapability(capability))
                    .filter(m -> m.getContextWindow() != null && m.getContextWindow() >= 100000)
                    .map(AiModel::getModelId)
                    .findFirst()
                    .orElse(null);
        }

        // 成本敏感 → 选最便宜的
        if (Boolean.TRUE.equals(features.get(CapabilityRoutingContext.FEATURE_COST_SENSITIVE))) {
            return modelRepository.findByEnabledTrueOrderBySortOrder().stream()
                    .filter(m -> m.hasCapability(capability))
                    .filter(m -> m.getInputPricePerK() != null)
                    .min((a, b) -> a.getInputPricePerK().compareTo(b.getInputPricePerK()))
                    .map(AiModel::getModelId)
                    .orElse(null);
        }

        return null;
    }
}
