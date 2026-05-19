/**
 * 技能定义实体——Agent + 工具 + Prompt 的可配置组合。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant;

import jakarta.persistence.*;

import com.xuejiai.aaf.common.model.BaseEntity;

import lombok.Getter;
import lombok.Setter;

/**
 * Skill = Agent 能力的可配置单元。
 * 一个 Assistant 持有多个 Skill，用户意图匹配到 Skill 后派发对应 Agent。
 */
@Getter
@Setter
@Entity
@Table(name = "ai_skill_definition", indexes = {
    @Index(columnList = "assistantId"),
    @Index(columnList = "triggerIntent")
})
public class SkillDefinition extends BaseEntity {

    /** 技能唯一标识 */
    @Column(nullable = false, unique = true, length = 64)
    private String skillId;

    /** 所属 Assistant ID */
    @Column(nullable = false, length = 64)
    private String assistantId;

    /** 显示名称 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 描述（用于意图匹配和展示） */
    @Column(length = 512)
    private String description;

    /** 绑定的 Agent ID */
    @Column(nullable = false, length = 64)
    private String agentId;

    /** 触发意图关键词（JSON 数组，如 ["代码审查","review"]） */
    @Column(columnDefinition = "TEXT")
    private String triggerIntent;

    /** 技能专属系统提示词（覆盖 Agent 默认提示词） */
    @Column(columnDefinition = "TEXT")
    private String systemPrompt;

    /** 绑定的工具列表（JSON 数组，覆盖 Agent 默认工具） */
    @Column(columnDefinition = "TEXT")
    private String tools;

    /** 优先级（数字越大优先级越高） */
    @Column(nullable = false)
    private Integer priority = 0;

    /** 状态：active / inactive */
    @Column(nullable = false, length = 16)
    private String status = "active";
}
