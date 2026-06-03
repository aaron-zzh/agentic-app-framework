/**
 * 模型注册实体。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.core.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @Column(nullable = false, unique = true, length = 128)
    private String modelId;

    /** 显示名称 */
    @Column(nullable = false, length = 128)
    private String displayName;

    /** 供应商配置 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private AiModelProvider providerConfig;

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

    /** 模型价格倍率（基础系数） */
    @Column(name = "model_ratio", precision = 10, scale = 6)
    private BigDecimal modelRatio = BigDecimal.ONE;

    /** 输出 Token 倍率 */
    @Column(name = "completion_ratio", precision = 10, scale = 6)
    private BigDecimal completionRatio = BigDecimal.ONE;

    /** 缓存 Token 倍率 */
    @Column(name = "cache_ratio", precision = 10, scale = 6)
    private BigDecimal cacheRatio;

    /** 音频输入倍率 */
    @Column(name = "audio_ratio", precision = 10, scale = 6)
    private BigDecimal audioRatio;

    /** 音频输出倍率 */
    @Column(name = "audio_completion_ratio", precision = 10, scale = 6)
    private BigDecimal audioCompletionRatio;

    /** 阶梯计费配置 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "step_ratios", columnDefinition = "jsonb")
    private JsonNode stepRatios;

    /** 第三方文档标签 */
    @Column(length = 200)
    private String tags;

    /** 模型类型 */
    @Column(length = 50)
    private String modelType;

    /** 支持的接口类型 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_endpoints", columnDefinition = "jsonb")
    private JsonNode supportedEndpoints;

    /** 配额类型 */
    private Short quotaType = 0;

    /** 固定价格 */
    @Column(name = "model_price", precision = 10, scale = 6)
    private BigDecimal modelPrice;

    /** 启用分组 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "enable_groups", columnDefinition = "jsonb")
    private JsonNode enableGroups;

    /** 来源侧供应商 ID */
    private Long vendorId;

    /** 来源侧供应商名称 */
    @Column(length = 100)
    private String vendorName;

    /** 来源侧供应商图标 */
    @Column(length = 100)
    private String vendorIcon;

    /** 模型图标 */
    @Column(length = 100)
    private String icon;

    /** 模型描述 */
    private String description;

    /** 官方定价参考 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "official_price", columnDefinition = "jsonb")
    private JsonNode officialPrice;

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

    /** 实际 API Base URL：模型级覆盖优先，其次使用供应商级配置。 */
    public String effectiveBaseUrl() {
        if (hasText(baseUrl)) {
            return baseUrl;
        }
        return providerConfig != null ? providerConfig.getBaseUrl() : null;
    }

    /** 实际 API Key：模型级覆盖优先，其次使用供应商级配置。 */
    public String effectiveApiKey() {
        if (hasText(apiKey)) {
            return apiKey;
        }
        return providerConfig != null ? providerConfig.getApiKey() : null;
    }

    /** 实际协议类型：模型级覆盖优先，其次使用供应商级配置。 */
    public String effectiveProviderType() {
        if (hasText(providerType)) {
            return providerType;
        }
        return providerConfig != null ? providerConfig.getProviderType() : PROVIDER_TYPE_OPENAI_COMPAT;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
