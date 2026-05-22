/**
 * AI 配置属性。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.ai.chat;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** AI 模块配置，管理多模型路由和系统提示词。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "aaf.ai")
public class AiProperties {

    /** 默认模型名称（chat 能力兜底，对应 models Map 中的 key） */
    private String defaultModel = "default";

    /** 降级模型名称 */
    private String fallbackModel;

    /**
     * 各能力兜底 modelId，key=capability（CHAT/IMAGE_GEN/VIDEO_GEN/SPEECH_ASR/SPEECH_TTS/RERANK/EMBEDDING）。
     * 优先级低于 DB（ai_model_preference），高于代码内置默认值。
     *
     * <p>yaml 示例：
     * <pre>
     * aaf:
     *   ai:
     *     default-models:
     *       IMAGE_GEN: qwen-image-plus
     *       VIDEO_GEN: wan2.6-i2v-flash
     *       SPEECH_ASR: qwen3-asr-flash
     *       SPEECH_TTS: cosyvoice-v3-flash
     *       RERANK: gte-rerank-v2
     *       EMBEDDING: text-embedding-v3
     * </pre>
     */
    private Map<String, String> defaultModels = new HashMap<>();

    /** 模型配置映射：场景名 → 模型配置 */
    private Map<String, ModelConfig> models = new HashMap<>();

    /** 系统提示词模板：场景名 → 提示词内容 */
    private Map<String, String> prompts = new HashMap<>();

    /** 单个模型配置 */
    @Getter
    @Setter
    public static class ModelConfig {

        /** 提供商：openai / ollama / anthropic */
        private String provider = "openai";

        /** API Key */
        private String apiKey;

        /** API 基础地址 */
        private String baseUrl;

        /** 模型名称 */
        private String model;

        /** 最大 Token 数 */
        private Integer maxTokens = 4096;

        /** 温度参数 */
        private Double temperature = 0.7;
    }
}
