package com.xuejiai.aaf.module.model.vo;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 创建模型请求。 */
public record AiModelCreateDTO(
        @NotBlank String modelId,
        @NotBlank String displayName,
        @NotBlank String provider,
        @NotBlank
                @Pattern(
                        regexp = "^(OPENAI_COMPAT|ANTHROPIC|OLLAMA)$",
                        message = "providerType 必须为 OPENAI_COMPAT / ANTHROPIC / OLLAMA")
                String providerType,
        @NotBlank String modelName,
        String baseUrl,
        /** 明文 apiKey，存储时加密 */
        String apiKey,
        String capabilities,
        Double temperature,
        Integer maxTokens,
        Integer contextWindow,
        BigDecimal inputPricePerK,
        BigDecimal outputPricePerK,
        String fallbackModelId,
        Integer sortOrder,
        String remark) {}
