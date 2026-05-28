package com.xuejiai.aaf.framework.intelligent.assistant;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.intelligent.core.memory.MemoryStrategy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Assistant 定义：Actor（人格）+ Role（能力）+ MemoryStrategy（记忆管道）的组合。 一个用户可拥有多个 Assistant，每个 Assistant
 * 有独立的人格、技能集和记忆策略。
 */
@Getter
@Setter
@Entity
@Table(
        name = "ai_assistant",
        indexes = {@Index(columnList = "userId"), @Index(columnList = "assistantId")})
public class AssistantDefinition extends BaseEntity {

    /** Assistant 唯一标识 */
    @Column(nullable = false, unique = true, length = 64)
    private String assistantId;

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

    /** 关联的 Actor ID（人格载体） */
    @Column(nullable = false, length = 64)
    private String actorId;

    /** 关联的 Role ID（能力配置） */
    @Column(nullable = false, length = 64)
    private String roleId;

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
