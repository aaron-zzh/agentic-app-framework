package com.xuejiai.aaf.framework.intelligent.core.context;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;

import io.agentscope.core.memory.autocontext.AutoContextConfig;
import lombok.RequiredArgsConstructor;

/** 上下文策略与预算服务。 */
@Service
@RequiredArgsConstructor
public class ContextPolicyService {

    private final ContextSettingsProvider settingsProvider;
    private final AiModelRepository modelRepository;

    public ContextBudget budget(String modelId, String policyValue) {
        var model =
                modelId != null
                        ? modelRepository.findByModelId(modelId).orElse(null)
                        : null;
        return budget(model, modelId, policyValue);
    }

    public ContextBudget budget(AiModel model, String fallbackModelId, String policyValue) {
        var config = settingsProvider.current();
        var policy =
                ContextPolicy.from(
                        policyValue != null ? policyValue : config.defaultPolicy());
        var contextWindow =
                positive(model != null ? model.getContextWindow() : null, config.defaultContextWindow());
        var reservedOutput =
                positive(model != null ? model.getMaxTokens() : null, config.reservedOutputTokens());
        var fixedPrompt = positive(config.fixedPromptBudget(), 4000);
        var inputBudget = Math.max(1024, contextWindow - reservedOutput - fixedPrompt);
        var ratio = ratioFor(policy, config.compressionTriggerRatio());
        var triggerTokens = Math.max(512, (int) Math.floor(inputBudget * ratio));
        var lastKeep = lastKeepFor(policy, positive(config.lastKeep(), 12));
        var messageThreshold = positive(config.messageThreshold(), 50);
        var largeInputThreshold = positive(config.largeInputCharThreshold(), 8000);
        var rulePreviewChars = positive(config.rulePreviewChars(), 1600);
        var resolvedModelId =
                model != null && model.getModelId() != null ? model.getModelId() : fallbackModelId;

        return new ContextBudget(
                resolvedModelId,
                policy,
                contextWindow,
                inputBudget,
                triggerTokens,
                reservedOutput,
                fixedPrompt,
                lastKeep,
                messageThreshold,
                largeInputThreshold,
                rulePreviewChars);
    }

    public AutoContextConfig toAutoContextConfig(AiModel model, String policyValue) {
        var budget = budget(model, model != null ? model.getModelId() : null, policyValue);
        return AutoContextConfig.builder()
                .maxToken(budget.inputBudget())
                .tokenRatio(Math.min(0.95, (double) budget.triggerTokens() / budget.inputBudget()))
                .msgThreshold(budget.messageThreshold())
                .lastKeep(budget.lastKeep())
                .largePayloadThreshold(budget.largeInputCharThreshold())
                .offloadSinglePreview(budget.rulePreviewChars())
                .minConsecutiveToolMessages(6)
                .currentRoundCompressionRatio(0.3)
                .build();
    }

    private int positive(Integer value, int fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    private int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private double ratioFor(ContextPolicy policy, double configured) {
        if (policy == ContextPolicy.AGGRESSIVE) {
            return 0.4;
        }
        if (policy == ContextPolicy.PRESERVE_RECENT) {
            return 0.7;
        }
        if (policy == ContextPolicy.FULL_DETAIL) {
            return 0.9;
        }
        return configured > 0 && configured <= 1 ? configured : 0.5;
    }

    private int lastKeepFor(ContextPolicy policy, int configured) {
        return switch (policy) {
            case AGGRESSIVE -> Math.min(configured, 8);
            case PRESERVE_RECENT -> Math.max(configured, 30);
            case FULL_DETAIL -> Math.max(configured, 40);
            default -> configured;
        };
    }
}
