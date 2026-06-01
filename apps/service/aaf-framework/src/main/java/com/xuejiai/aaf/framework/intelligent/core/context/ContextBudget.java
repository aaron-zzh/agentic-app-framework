package com.xuejiai.aaf.framework.intelligent.core.context;

/** 本次模型调用的上下文预算。 */
public record ContextBudget(
        String modelId,
        ContextPolicy policy,
        int contextWindow,
        int inputBudget,
        int triggerTokens,
        int reservedOutputTokens,
        int fixedPromptBudget,
        int lastKeep,
        int messageThreshold,
        int largeInputCharThreshold,
        int rulePreviewChars) {}
