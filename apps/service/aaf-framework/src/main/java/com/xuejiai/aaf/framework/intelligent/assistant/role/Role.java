package com.xuejiai.aaf.framework.intelligent.assistant.role;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.org.OrgIgnore;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Role（能力配置）：定义可复用的技能集与工具授权池。 可跨助理复用——同一个 Role 可经 ai_assistant_role 挂载到多个 Assistant。
 *
 * <p>标注 {@link OrgIgnore}：角色的可见性与归属完全由 {@code visibility} 与 {@code ownerId} 决定，不再有组织、工作区维度， {@code
 * org_id}、{@code workspace_id} 恒为 NULL。读取入口必须经 {@link RoleVisibility}，不得绕过可见性直接读取。
 */
@Getter
@Setter
@Entity(name = "AiRole")
@Table(name = "ai_role")
@OrgIgnore
public class Role extends BaseEntity {

    /** 可用状态。 */
    public static final String STATUS_ACTIVE = "active";

    /** 下架状态：已绑定的助理在加载时跳过，角色库不再展示。 */
    public static final String STATUS_DISABLED = "disabled";

    /** 稳定业务码。 */
    @Column(nullable = false, length = 64)
    private String code;

    /** 显示名称（如"代码助理能力集"） */
    @Column(nullable = false, length = 128)
    private String name;

    /** 描述 */
    @Column(length = 512)
    private String description;

    /** 绑定的 SkillBinding 对象数组（JSONB，由 {@code skill_ids @> ...} 支持反向引用查询）。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_ids", columnDefinition = "jsonb")
    private String skillIds;

    /**
     * 工具授权池（JSON 数组，角色维度）。 对应 engine/tool/ToolRegistry 中注册的工具名；运行时与 Agent 级 allowedTools 取交集后收窄。
     */
    @Column(columnDefinition = "TEXT")
    private String toolWhitelist;

    /** 状态：active / disabled（下架）。 */
    @Column(nullable = false, length = 16)
    private String status = STATUS_ACTIVE;

    /** 可见性：SYSTEM / PUBLIC / PRIVATE；WORKSPACE、ORG 仅预留，保存时拒绝。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RoleVisibilityLevel visibility;
}
