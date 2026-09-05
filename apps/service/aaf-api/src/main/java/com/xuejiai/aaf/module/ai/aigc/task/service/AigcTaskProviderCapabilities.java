package com.xuejiai.aaf.module.ai.aigc.task.service;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.core.model.AiModelProviderType;

/** Task provider 提交语义声明；未显式声明的 provider 一律按不可安全恢复处理。 */
@Component
public class AigcTaskProviderCapabilities {

    private static final Capabilities NO_RECOVERY = new Capabilities(false, false);

    public Capabilities require(AiModelProviderType providerType) {
        if (providerType == null) {
            throw new IllegalStateException("Task provider 类型不能为空");
        }
        return switch (providerType) {
            case OPENAI_COMPAT,
                    ANTHROPIC,
                    OLLAMA,
                    DASHSCOPE,
                    VOLCENGINE,
                    MIDJOURNEY,
                    MESHY -> NO_RECOVERY;
        };
    }

    public Capabilities require(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalStateException("Task provider 标识不能为空");
        }
        try {
            return require(AiModelProviderType.valueOf(provider.trim().toUpperCase()));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Task provider 能力未声明: " + provider, error);
        }
    }

    public record Capabilities(boolean idempotentSubmission, boolean receiptLookup) {

        public boolean recoverableAfterSubmittingCrash() {
            return idempotentSubmission && receiptLookup;
        }
    }
}
