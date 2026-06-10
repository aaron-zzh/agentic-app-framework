package com.xuejiai.aaf.framework.intelligent.assistant.role;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Role（能力配置）：定义可复用的技能集与工具授权池。 可跨助理复用——同一个 Role 可经 ai_assistant_role 挂载到多个 Assistant。 */
@Getter
@Setter
@Entity(name = "AiRole")
@Table(name = "ai_role")
public class Role extends BaseEntity {

    /** 显示名称（如"代码助理能力集"） */
    @Column(nullable = false, length = 128)
    private String name;

    /** 描述 */
    @Column(length = 512)
    private String description;

    /** 绑定的技能 ID 列表（JSON 数组）。 对应 engine/skill/SkillDefinition.skillId。 */
    @Column(columnDefinition = "TEXT")
    private String skillIds;

    /**
     * 工具授权池（JSON 数组，角色维度）。 对应 engine/tool/ToolRegistry 中注册的工具名；运行时与 Agent 级 allowedTools 取交集后收窄。
     */
    @Column(columnDefinition = "TEXT")
    private String toolWhitelist;

    /** 状态：active / inactive */
    @Column(nullable = false, length = 16)
    private String status = "active";
}
