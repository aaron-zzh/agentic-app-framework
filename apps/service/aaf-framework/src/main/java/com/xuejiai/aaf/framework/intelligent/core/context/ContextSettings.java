package com.xuejiai.aaf.framework.intelligent.core.context;

/** 上下文策略运行参数。 */
public record ContextSettings(
        boolean enabled,
        String defaultPolicy,
        int defaultContextWindow,
        int reservedOutputTokens,
        int fixedPromptBudget,
        double compressionTriggerRatio,
        int lastKeep,
        int messageThreshold,
        int largeInputCharThreshold,
        int rulePreviewChars,
        boolean enableSummary,
        String summaryModelId,
        long summaryTimeoutMs,
        String summarySystemPrompt,
        String summaryUserPrompt) {}
