package com.xuejiai.aaf.framework.intelligent.core.model;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** AI 模型供应商配置，同一供应商共享 baseUrl 与 API Key。 */
@Getter
@Setter
@Entity
@Table(name = "ai_model_provider")
@SQLDelete(sql = "UPDATE ai_model_provider SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AiModelProvider extends BaseEntity {

    /** 供应商编码：aliyun / volcengine / deepseek / third_party 等 */
    @Column(name = "provider_code", nullable = false, unique = true, length = 64)
    private String providerCode;

    /** 供应商名称 */
    @Column(name = "provider_name", nullable = false, length = 128)
    private String providerName;

    /** 默认协议类型 */
    @Column(nullable = false, length = 32)
    private String providerType = AiModel.PROVIDER_TYPE_OPENAI_COMPAT;

    /** 默认 API Base URL */
    @Column(length = 512)
    private String baseUrl;

    /** 供应商级 API Key（加密存储） */
    @Column(name = "api_key_encrypted", length = 1024)
    private String apiKey;

    /** 是否启用 */
    @Column(nullable = false)
    private Boolean enabled = true;

    /** 排序权重 */
    private Integer sortOrder = 100;

    /** 图标 */
    @Column(length = 100)
    private String icon;

    /** 描述 */
    private String description;
}
