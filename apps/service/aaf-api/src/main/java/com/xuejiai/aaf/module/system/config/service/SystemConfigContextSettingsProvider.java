package com.xuejiai.aaf.module.system.config.service;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.framework.intelligent.ai.chat.AiProperties;
import com.xuejiai.aaf.framework.intelligent.core.context.ContextSettings;
import com.xuejiai.aaf.framework.intelligent.core.context.ContextSettingsProvider;

import lombok.RequiredArgsConstructor;

/** 基于 sys_config 的上下文策略参数提供者。 */
@Primary
@Service
@RequiredArgsConstructor
public class SystemConfigContextSettingsProvider implements ContextSettingsProvider {

    private final SystemConfigService systemConfigService;
    private final AiProperties aiProperties;

    @Override
    public ContextSettings current() {
        var defaults = aiProperties.getContext();
        return new ContextSettings(
                bool(SysConfigKeys.Ai.CONTEXT_ENABLED, defaults.getEnabled()),
                string(SysConfigKeys.Ai.CONTEXT_DEFAULT_POLICY, defaults.getDefaultPolicy()),
                integer(SysConfigKeys.Ai.CONTEXT_DEFAULT_WINDOW, defaults.getDefaultContextWindow()),
                integer(SysConfigKeys.Ai.CONTEXT_RESERVED_OUTPUT_TOKENS, defaults.getReservedOutputTokens()),
                integer(SysConfigKeys.Ai.CONTEXT_FIXED_PROMPT_BUDGET, defaults.getFixedPromptBudget()),
                decimal(SysConfigKeys.Ai.CONTEXT_TRIGGER_RATIO, defaults.getCompressionTriggerRatio()),
                integer(SysConfigKeys.Ai.CONTEXT_LAST_KEEP, defaults.getLastKeep()),
                integer(SysConfigKeys.Ai.CONTEXT_MESSAGE_THRESHOLD, defaults.getMessageThreshold()),
                integer(SysConfigKeys.Ai.CONTEXT_LARGE_INPUT_THRESHOLD, defaults.getLargeInputCharThreshold()),
                integer(SysConfigKeys.Ai.CONTEXT_RULE_PREVIEW_CHARS, defaults.getRulePreviewChars()),
                bool(SysConfigKeys.Ai.CONTEXT_ENABLE_SUMMARY, defaults.getEnableSummary()),
                string(SysConfigKeys.Ai.CONTEXT_SUMMARY_MODEL_ID, defaults.getSummaryModelId()),
                integer(
                        SysConfigKeys.Ai.CONTEXT_SUMMARY_TIMEOUT_MS,
                        defaults.getSummaryTimeoutMs() != null
                                ? defaults.getSummaryTimeoutMs().intValue()
                                : 8000),
                string(SysConfigKeys.Ai.CONTEXT_SUMMARY_SYSTEM_PROMPT, defaults.getSummarySystemPrompt()),
                string(SysConfigKeys.Ai.CONTEXT_SUMMARY_USER_PROMPT, defaults.getSummaryUserPrompt()));
    }

    private String string(String key, String fallback) {
        return systemConfigService.getString(key, fallback);
    }

    private int integer(String key, Integer fallback) {
        return systemConfigService.getInteger(key, fallback != null ? fallback : 0);
    }

    private long integer(String key, int fallback) {
        return systemConfigService.getInteger(key, fallback);
    }

    private boolean bool(String key, Boolean fallback) {
        return systemConfigService.getBoolean(key, fallback != null ? fallback : false);
    }

    private double decimal(String key, Double fallback) {
        var value = systemConfigService.getString(key);
        if (value == null || value.isBlank()) {
            return fallback != null ? fallback : 0.5;
        }
        return Double.parseDouble(value);
    }
}
