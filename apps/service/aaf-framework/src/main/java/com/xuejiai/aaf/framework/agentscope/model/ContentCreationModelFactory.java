/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xuejiai.aaf.framework.agentscope.config.ContentCreationProperties;
import com.xuejiai.aaf.framework.intelligent.ai.chat.AiProperties;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelProviderType;

import io.agentscope.core.model.AnthropicChatModel;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;

/**
 * 模型工厂：按 {@link ContentCreationProperties#getModelId()} 字符串协议解析对应的 SDK Builder。
 *
 * <p>支持前缀：
 *
 * <ul>
 *   <li>{@code dashscope:<modelName>} — 默认；读取环境变量 {@code DASHSCOPE_API_KEY}
 *   <li>{@code openai:<modelName>} — 读取 {@code OPENAI_API_KEY} 与可选 {@code OPENAI_BASE_URL}
 *   <li>{@code anthropic:<modelName>} — 读取 {@code ANTHROPIC_API_KEY} 与可选 {@code ANTHROPIC_BASE_URL}
 * </ul>
 *
 * <p>开发期临时方案：直接读环境变量。生产链路建议改造成从 AAF 的 {@code ai_model_provider} + {@code ai_model} 表里按 {@code
 * modelId} 查询，再用解密后的 API Key 构造 Builder。
 */
public final class ContentCreationModelFactory {

    private static final Logger log = LoggerFactory.getLogger(ContentCreationModelFactory.class);

    private ContentCreationModelFactory() {}

    /** 按 props 配置构建主模型（如有 {@code fallbackModelId} 则不在此处包装，由 HarnessAgent 自身降级）。 */
    public static Model build(ContentCreationProperties props) {
        return buildById(props.getModelId());
    }

    public static Model buildFallback(ContentCreationProperties props) {
        var fb = props.getFallbackModelId();
        if (fb == null || fb.isBlank()) {
            return null;
        }
        return buildById(fb.trim());
    }

    /**
     * 从 AAF {@link AiModel} 实体（经六层决策链解析后）构建 agentscope {@link Model}。
     *
     * <p>apiKey/baseUrl 优先级：模型级 → {@code aaf.ai.models.{provider}} → {@code aaf.ai.models.default}
     */
    public static Model buildFromAiModel(AiModel aiModel) {
        return buildFromAiModel(aiModel, null);
    }

    public static Model buildFromAiModel(AiModel aiModel, AiProperties aiProperties) {
        var providerType = aiModel.effectiveProviderType();
        var apiKey = aiModel.effectiveApiKey();
        var baseUrl = aiModel.effectiveBaseUrl();

        // yaml 兜底：模型级 key/url 为空时从 aaf.ai.models.{provider} 或 default 取
        if (aiProperties != null && (apiKey == null || apiKey.isBlank() || baseUrl == null || baseUrl.isBlank())) {
            var models = aiProperties.getModels();
            var cfg = models.getOrDefault(aiModel.getProvider(), models.get("default"));
            if (cfg != null) {
                if (apiKey == null || apiKey.isBlank()) apiKey = cfg.getApiKey();
                if (baseUrl == null || baseUrl.isBlank()) baseUrl = cfg.getBaseUrl();
            }
        }

        var modelName = aiModel.getModelName();
        log.info(
                "[ContentCreation] 六层决策链选模型: {} ({}) baseUrl={}",
                aiModel.getModelId(),
                providerType,
                baseUrl);

        if (providerType == AiModelProviderType.ANTHROPIC) {
            var b = AnthropicChatModel.builder().apiKey(apiKey).modelName(modelName).stream(true);
            if (baseUrl != null && !baseUrl.isBlank()) b.baseUrl(baseUrl);
            return b.build();
        }
        if (providerType == AiModelProviderType.DASHSCOPE) {
            return DashScopeChatModel.builder().apiKey(apiKey).modelName(modelName).stream(true)
                    .build();
        }
        // OPENAI_COMPAT / OLLAMA / 其他 → OpenAI 兼容
        var b = OpenAIChatModel.builder().apiKey(apiKey).modelName(modelName).stream(true);
        if (baseUrl != null && !baseUrl.isBlank()) b.baseUrl(baseUrl);
        return b.build();
    }

    private static Model buildById(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId 不能为空");
        }
        if (modelId.startsWith("openai:")) {
            var name = modelId.substring("openai:".length());
            var apiKey = System.getenv("OPENAI_API_KEY");
            var baseUrl = System.getenv("OPENAI_BASE_URL");
            var b = OpenAIChatModel.builder().apiKey(apiKey).modelName(name).stream(true);
            if (baseUrl != null && !baseUrl.isBlank()) {
                b.baseUrl(baseUrl);
            }
            log.info("[ContentCreation] 主模型: openai/{}", name);
            return b.build();
        }
        if (modelId.startsWith("anthropic:")) {
            var name = modelId.substring("anthropic:".length());
            var apiKey = System.getenv("ANTHROPIC_API_KEY");
            var baseUrl = System.getenv("ANTHROPIC_BASE_URL");
            var b = AnthropicChatModel.builder().apiKey(apiKey).modelName(name).stream(true);
            if (baseUrl != null && !baseUrl.isBlank()) {
                b.baseUrl(baseUrl);
            }
            log.info("[ContentCreation] 主模型: anthropic/{}", name);
            return b.build();
        }
        var name =
                modelId.startsWith("dashscope:")
                        ? modelId.substring("dashscope:".length())
                        : modelId;
        var apiKey = System.getenv("DASHSCOPE_API_KEY");
        log.info("[ContentCreation] 主模型: dashscope/{}", name);
        return DashScopeChatModel.builder().apiKey(apiKey).modelName(name).stream(true).build();
    }
}
