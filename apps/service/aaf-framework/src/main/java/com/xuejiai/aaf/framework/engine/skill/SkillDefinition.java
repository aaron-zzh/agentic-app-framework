package com.xuejiai.aaf.framework.engine.skill;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 技能定义实体（迁移自 intelligent/assistant/SkillDefinition）。 扩展：builtIn（内置标记）、version（版本号，用于启动时
 * upsert）、instructions（技能指令）。
 */
@Getter
@Setter
@Entity
@Table(
        name = "ai_skill_definition",
        indexes = {
            @Index(columnList = "assistantId"),
            @Index(columnList = "triggerIntent"),
            @Index(columnList = "builtIn")
        })
public class SkillDefinition extends BaseEntity {

    /** 技能唯一标识 */
    @Column(nullable = false, unique = true, length = 64)
    private String skillId;

    /** 所属 Assistant ID（内置技能为 null，表示全局可用） */
    @Column(length = 64)
    private String assistantId;

    /** 显示名称 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 描述（用于意图匹配和展示） */
    @Column(length = 512)
    private String description;

    /** 绑定的 Agent ID（null 表示 Assistant 直接处理） */
    @Column(length = 64)
    private String agentId;

    /** 触发意图关键词（JSON 数组，如 ["代码审查","review"]） */
    @Column(columnDefinition = "TEXT")
    private String triggerIntent;

    /** 技能指令（Markdown 格式，对应 SKILL.md 正文） */
    @Column(columnDefinition = "TEXT")
    private String instructions;

    /** 技能专属系统提示词（覆盖 Agent 默认提示词） */
    @Column(columnDefinition = "TEXT")
    private String systemPrompt;

    /** 绑定的工具列表（JSON 数组） */
    @Column(columnDefinition = "TEXT")
    private String tools;

    /** 优先级（数字越大优先级越高） */
    @Column(nullable = false)
    private Integer priority = 0;

    /** 是否内置（内置技能不可删除，可被同名用户技能覆盖） */
    @Column(nullable = false)
    private Boolean builtIn = false;

    /** 是否全局（全局技能注入到所有 Agent 的 SkillBox） */
    @Column(name = "is_global", nullable = false)
    private Boolean isGlobal = false;

    /** 版本号（内置技能升级时用于 upsert 判断） */
    @Column(name = "skill_version", length = 16)
    private String skillVersion;

    /** 状态：active / inactive */
    @Column(nullable = false, length = 16)
    private String status = "active";
}
