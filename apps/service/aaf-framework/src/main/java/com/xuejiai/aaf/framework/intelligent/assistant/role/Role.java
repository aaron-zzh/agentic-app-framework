package com.xuejiai.aaf.framework.intelligent.assistant.role;

import jakarta.persistence.*;

import com.xuejiai.aaf.common.model.BaseEntity;

import lombok.Getter;
import lombok.Setter;

/**
 * Role（能力配置）：定义助理的技能集和工具白名单。
 * 可复用——同一个 Role 可绑定不同 Actor 组成多个 Assistant。
 */
@Getter
@Setter
@Entity
@Table(name = "ai_role")
public class Role extends BaseEntity {

    /** Role 唯一标识 */
    @Column(nullable = false, unique = true, length = 64)
    private String roleId;

    /** 显示名称（如"代码助理能力集"） */
    @Column(nullable = false, length = 128)
    private String name;

    /** 描述 */
    @Column(length = 512)
    private String description;

    /**
     * 绑定的技能 ID 列表（JSON 数组）。
     * 对应 engine/skill/SkillDefinition.skillId。
     */
    @Column(columnDefinition = "TEXT")
    private String skillIds;

    /**
     * 工具白名单（JSON 数组，assistantId 维度）。
     * 对应 engine/tool/ToolRegistry 中注册的工具名。
     */
    @Column(columnDefinition = "TEXT")
    private String toolWhitelist;

    /** 状态：active / inactive */
    @Column(nullable = false, length = 16)
    private String status = "active";
}
