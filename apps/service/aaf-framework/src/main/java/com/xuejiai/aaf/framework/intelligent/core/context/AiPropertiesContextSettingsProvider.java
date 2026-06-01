package com.xuejiai.aaf.framework.intelligent.core.context;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.ai.chat.AiProperties;

import lombok.RequiredArgsConstructor;

/** 基于配置文件的上下文参数提供者。 */
@Service
@ConditionalOnMissingBean(ContextSettingsProvider.class)
@RequiredArgsConstructor
public class AiPropertiesContextSettingsProvider implements ContextSettingsProvider {

    private final AiProperties aiProperties;

    @Override
    public ContextSettings current() {
        var c = aiProperties.getContext();
        return new ContextSettings(
                Boolean.TRUE.equals(c.getEnabled()),
                c.getDefaultPolicy(),
                intValue(c.getDefaultContextWindow(), 128000),
                intValue(c.getReservedOutputTokens(), 4096),
                intValue(c.getFixedPromptBudget(), 4000),
                doubleValue(c.getCompressionTriggerRatio(), 0.5),
                intValue(c.getLastKeep(), 12),
                intValue(c.getMessageThreshold(), 50),
                intValue(c.getLargeInputCharThreshold(), 8000),
                intValue(c.getRulePreviewChars(), 1600),
                Boolean.TRUE.equals(c.getEnableSummary()),
                c.getSummaryModelId(),
                longValue(c.getSummaryTimeoutMs(), 8000L),
                c.getSummarySystemPrompt(),
                c.getSummaryUserPrompt());
    }

    private int intValue(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private long longValue(Long value, long fallback) {
        return value != null ? value : fallback;
    }

    private double doubleValue(Double value, double fallback) {
        return value != null ? value : fallback;
    }
}
