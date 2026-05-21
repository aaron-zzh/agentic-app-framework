/**
 * 模型注册实体。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.core.model;

import java.math.BigDecimal;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * LLM 模型注册信息，持久化到数据库，支持动态启用/禁用。
 *
 * <p>providerType 决定运行时使用哪个 SDK：
 *
 * <ul>
 *   <li>OPENAI_COMPAT — OpenAI 兼容接口（覆盖绝大多数厂商和聚合平台）
 *   <li>ANTHROPIC — Anthropic 原生 Messages API
 *   <li>OLLAMA — 本地 Ollama 部署
 * </ul>
 *
 * <p>provider 是来源标识（openai / deepseek / qwen / openrouter 等），自由文本。
 */
@Getter
@Setter
@Entity
@Table(name = "ai_model")
public class AiModel extends BaseEntity {

    /** 模型唯一标识（如 openai:gpt-4o, deepseek:chat） */
    @Column(nullable = false, unique = true, length = 64)
    private String modelId;

    /** 显示名称 */
    @Column(nullable = false, length = 128)
    private String displayName;

    /** 来源标识：openai / deepseek / qwen / moonshot / zhipu / anthropic / ollama / openrouter 等 */
    @Column(nullable = false, length = 32)
    private String provider;

    /**
     * 协议类型，决定运行时 SDK 选择。
     *
     * @see #PROVIDER_TYPE_OPENAI_COMPAT
     * @see #PROVIDER_TYPE_ANTHROPIC
     * @see #PROVIDER_TYPE_OLLAMA
     */
    @Column(nullable = false, length = 32)
    private String providerType = PROVIDER_TYPE_OPENAI_COMPAT;

    public static final String PROVIDER_TYPE_OPENAI_COMPAT = "OPENAI_COMPAT";
    public static final String PROVIDER_TYPE_ANTHROPIC = "ANTHROPIC";
    public static final String PROVIDER_TYPE_OLLAMA = "OLLAMA";

    /** 模型名称（发送给 API 的实际名称） */
    @Column(nullable = false, length = 128)
    private String modelName;

    /** API Base URL */
    @Column(length = 512)
    private String baseUrl;

    /** API Key（加密存储） */
    @Column(name = "api_key_encrypted", length = 1024)
    private String apiKey;

    /** 模型能力，逗号分隔：CHAT / EMBEDDING / VISION / IMAGE_GEN / AUDIO / RERANK */
    @Column(length = 256)
    private String capabilities = "CHAT";

    /** 默认温度 */
    private Double temperature;

    /** 默认最大输出 Token */
    private Integer maxTokens;

    /** 上下文窗口大小 */
    private Integer contextWindow;

    /** 输入 Token 单价（元/千Token，用于积分结算） */
    @Column(name = "input_price_per_k", precision = 10, scale = 6)
    private BigDecimal inputPricePerK;

    /** 输出 Token 单价（元/千Token） */
    @Column(name = "output_price_per_k", precision = 10, scale = 6)
    private BigDecimal outputPricePerK;

    /** 是否启用 */
    @Column(nullable = false)
    private Boolean enabled = true;

    /** 降级模型 ID */
    @Column(length = 64)
    private String fallbackModelId;

    /** 排序权重（越小越优先） */
    private Integer sortOrder = 100;

    /** 备注 */
    @Column(length = 512)
    private String remark;

    /** 是否支持指定能力 */
    public boolean hasCapability(String capability) {
        return capabilities != null && capabilities.contains(capability);
    }
}
