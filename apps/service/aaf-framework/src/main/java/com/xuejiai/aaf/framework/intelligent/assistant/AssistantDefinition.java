package com.xuejiai.aaf.framework.intelligent.assistant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.intelligent.core.memory.MemoryStrategy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Assistant 定义：Persona（人格）+ Role（能力）+ MemoryStrategy（记忆管道）的组合。 一个用户可拥有多个 Assistant，每个 Assistant
 * 有独立的人格、技能集和记忆策略。
 */
@Getter
@Setter
@Entity
@Table(
        name = "ai_assistant",
        indexes = {@Index(columnList = "userId")})
public class AssistantDefinition extends BaseEntity {

    /** 所属用户 ID */
    @Column(nullable = false)
    private Long userId;

    /** 委托者 ID（权限继承来源，通常等于 userId） */
    @Column(name = "delegator_id")
    private Long delegatorId;

    /** 权限边界配置（JSON 存储） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permission_scope", columnDefinition = "jsonb")
    private PermissionScope permissionScope;

    /** 关联的 Persona ID（人格载体） */
    @Column(nullable = false)
    private Long personaId;

    /** 默认 Role ID（助理下可有多个 Role，此为默认使用的） */
    @Column(nullable = false)
    private Long defaultRoleId;

    /** 记忆管道策略（默认混合检索） */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MemoryStrategy memoryStrategy = MemoryStrategy.HYBRID;

    /** 关联的知识库 ID（可选） */
    private Long knowledgeBaseId;

    /** 状态：active / inactive */
    @Column(nullable = false, length = 16)
    private String status = "active";

    /** 获取有效的委托者 ID（未设置时回退到 userId） */
    public Long getEffectiveDelegatorId() {
        return delegatorId != null ? delegatorId : userId;
    }

    /** 获取有效的权限边界（未设置时使用默认配置） */
    public PermissionScope getEffectiveScope() {
        return permissionScope != null ? permissionScope : PermissionScope.defaults();
    }
}
