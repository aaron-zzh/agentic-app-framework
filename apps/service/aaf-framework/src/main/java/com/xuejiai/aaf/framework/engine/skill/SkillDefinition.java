package com.xuejiai.aaf.framework.engine.skill;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
            @Index(columnList = "triggerIntent"),
            @Index(columnList = "builtIn"),
            @Index(
                    name = "idx_skill_public_scope",
                    columnList = "is_public,org_id,workspace_id,status"),
            @Index(
                    name = "idx_skill_owner_scope",
                    columnList = "owner_id,org_id,workspace_id,status")
        })
@SQLDelete(
        sql =
                "UPDATE ai_skill_definition SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class SkillDefinition extends BaseEntity {

    /** 业务唯一码（用于前端 deep-link，如 ?skill=voiceover；nullable 容忍历史技能） */
    @Column(length = 100, unique = true)
    private String code;

    /** 显示名称 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 描述（用于意图匹配和展示） */
    @Column(length = 512)
    private String description;

    /** 技能分类（如 COPYWRITING/STRATEGY/CODE/ANALYSIS，前端按此过滤展示） */
    @Column(length = 50)
    private String category;

    /** 绑定的 Agent ID（null 表示 Assistant 直接处理） */
    @Column private Long agentId;

    /** 触发意图关键词（JSON 数组，如 ["代码审查","review"]） */
    @Column(columnDefinition = "TEXT")
    private String triggerIntent;

    /** 技能指令（Markdown 格式，对应 SKILL.md 正文） */
    @Column(columnDefinition = "TEXT")
    private String instructions;

    /** 技能专属系统提示词（覆盖 Agent 默认提示词） */
    @Column(columnDefinition = "TEXT")
    private String systemPrompt;

    /** 优先级（数字越大优先级越高） */
    @Column(nullable = false)
    private Integer priority = 0;

    /** 是否内置（内置技能不可删除，可被同名用户技能覆盖） */
    @Column(nullable = false)
    private Boolean builtIn = false;

    /** 是否全局（全局技能注入到所有 Agent 的 SkillBox） */
    @Column(name = "is_global", nullable = false)
    private Boolean isGlobal = false;

    /** 是否公开展示给当前组织/工作区用户。 */
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = false;

    /** 版本号（内置技能升级时用于 upsert 判断） */
    @Column(name = "skill_version", length = 16)
    private String skillVersion;

    /** 状态：active / inactive */
    @Column(nullable = false, length = 16)
    private String status = "active";
}
