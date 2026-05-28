package com.xuejiai.aaf.framework.intelligent.core.model;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 模型偏好配置。
 *
 * <p>支持两种 scope：
 *
 * <ul>
 *   <li>USER — 用户级别，优先于系统默认
 *   <li>SYSTEM — 系统级别，管理员配置的全局默认
 * </ul>
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

    /** 作用域：USER / SYSTEM */
    @Column(nullable = false, length = 16)
    private String scope;

    /** 作用域 ID：userId（SYSTEM 时为 null） */
    @Column(name = "scope_id")
    private Long scopeId;

    /** 能力类型：CHAT / EMBEDDING / IMAGE_GEN / SPEECH_ASR 等 */
    @Column(nullable = false, length = 32)
    private String capability;

    /** 指向 ai_model.model_id */
    @Column(nullable = false, length = 64)
    private String modelId;
}
