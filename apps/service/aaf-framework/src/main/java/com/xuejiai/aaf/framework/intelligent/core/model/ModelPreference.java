package com.xuejiai.aaf.framework.intelligent.core.model;

import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 模型偏好配置。
 *
 * <p>model_ids 是有序渠道列表，RouterCapability 按顺序取第一个可用模型，其余作为降级链。
 */
@Getter
@Setter
@Entity
@Table(
        name = "ai_model_preference",
        uniqueConstraints = @UniqueConstraint(columnNames = {"scope", "scope_id", "capability"}))
public class ModelPreference extends BaseEntity {

    public static final String SCOPE_USER = "USER";
    public static final String SCOPE_SYSTEM = "SYSTEM";

    @Column(nullable = false, length = 16)
    private String scope;

    @Column(name = "scope_id")
    private Long scopeId;

    @Column(nullable = false, length = 32)
    private String capability;

    /** 有序渠道列表，如 ["n1n:gpt-4o","openrouter:gpt-4o","openai:gpt-4o"] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> modelIds;

    /** 取第一个 model_id（兼容旧的单值场景） */
    public String getPrimaryModelId() {
        return modelIds != null && !modelIds.isEmpty() ? modelIds.get(0) : null;
    }
}
