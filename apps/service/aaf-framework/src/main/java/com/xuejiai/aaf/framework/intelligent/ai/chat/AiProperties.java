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

    /** 默认模型名称（chat 能力兜底，对应 DB ai_model.model_id，格式：{provider}:{name}） */
    private String defaultModel = "deepseek:chat";

    /** 降级模型名称 */
    private String fallbackModel;

    /**
     * 各能力兜底
     * modelId，key=capability（CHAT/IMAGE_GEN/VIDEO_GEN/SPEECH_ASR/SPEECH_TTS/RERANK/EMBEDDING）。
     * 优先级低于 DB（ai_model_preference），高于代码内置默认值。
     *
     * <p>yaml 示例：
     *
     * <pre>
     * aaf:
     *   ai:
     *     default-models:
     *       IMAGE_GEN: qwen-image-plus
     *       VIDEO_GEN: happyhorse-1.0-i2v
     *       SPEECH_ASR: fun-asr-realtime
     *       SPEECH_TTS: cosyvoice-v3-flash
     *       RERANK: qwen3-rerank
     *       EMBEDDING: text-embedding-v3
     * </pre>
     */
    private Map<String, String> defaultModels = new HashMap<>();

    /** 模型配置映射：场景名 → 模型配置 */
    private Map<String, ModelConfig> models = new HashMap<>();

    /** 系统提示词模板：场景名 → 提示词内容 */
    private Map<String, String> prompts = new HashMap<>();

    /** 上下文压缩策略配置 */
    private ContextConfig context = new ContextConfig();

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

    /** 上下文预算与压缩配置。 */
    @Getter
    @Setter
    public static class ContextConfig {

        /** 是否启用输入前上下文压缩。 */
        private Boolean enabled = true;

        /** 默认策略：balanced / aggressive / preserve-recent / full-detail。 */
        private String defaultPolicy = "balanced";

        /** 未配置模型窗口时使用的默认上下文窗口。 */
        private Integer defaultContextWindow = 128000;

        /** 为模型输出预留的 Token 数。 */
        private Integer reservedOutputTokens = 4096;

        /** 为系统提示词、工具定义等固定内容预留的 Token 数。 */
        private Integer fixedPromptBudget = 4000;

        /** 达到输入预算该比例时触发压缩。 */
        private Double compressionTriggerRatio = 0.5;

        /** 最近保留的消息数。 */
        private Integer lastKeep = 12;

        /** 消息数阈值，超过后即使 Token 未超也进入压缩判断。 */
        private Integer messageThreshold = 50;

        /** 单条消息超过该字符数时先按规则裁剪。 */
        private Integer largeInputCharThreshold = 8000;

        /** 规则裁剪保留的预览字符数。 */
        private Integer rulePreviewChars = 1600;

        /** 规则裁剪后仍超预算时是否启用摘要模型。 */
        private Boolean enableSummary = true;

        /** 摘要模型 ID；为空时使用本次主模型。 */
        private String summaryModelId;

        /** 摘要模型超时时间（毫秒）。 */
        private Long summaryTimeoutMs = 8000L;

        /** 摘要系统提示词，可通过系统参数覆盖。 */
        private String summarySystemPrompt =
                "你是 AAF 的上下文压缩器。你的任务是压缩历史上下文，保留用户目标、关键事实、已确认决策、约束、未完成事项和必要引用。只输出可继续推理的摘要，不要输出解释。";

        /** 摘要用户提示词模板，支持 ${budgetTokens} 和 ${messages}。 */
        private String summaryUserPrompt =
                """
                请将以下对话上下文压缩到不超过 ${budgetTokens} tokens。

                保留：
                - 当前任务目标和用户明确要求
                - 关键业务数据、ID、路径、错误信息
                - 已做决策和不可违反约束
                - 未完成的下一步

                可以删除：
                - 重复寒暄
                - 已被后续内容覆盖的中间过程
                - 大段原始数据中的低价值细节

                待压缩上下文：
                ${messages}
                """;
    }
}
