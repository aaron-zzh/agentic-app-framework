/**
 * 模型注册实体。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.core.model;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** LLM 模型注册信息，持久化到数据库，支持动态启用/禁用。 */
@Getter
@Setter
@Entity
@Table(name = "ai_model")
public class AiModel extends BaseEntity {

    /** 模型唯一标识（如 gpt-4o, qwen-max） */
    @Column(nullable = false, unique = true, length = 64)
    private String modelId;

    /** 显示名称 */
    @Column(nullable = false, length = 128)
    private String displayName;

    /** 提供商：openai / anthropic / dashscope / ollama */
    @Column(nullable = false, length = 32)
    private String provider;

    /** 模型名称（发送给 API 的实际名称） */
    @Column(nullable = false, length = 128)
    private String modelName;

    /** API Base URL */
    @Column(length = 512)
    private String baseUrl;

    /** 默认温度 */
    private Double temperature;

    /** 默认最大输出 Token */
    private Integer maxTokens;

    /** 上下文窗口大小 */
    private Integer contextWindow;

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
}
