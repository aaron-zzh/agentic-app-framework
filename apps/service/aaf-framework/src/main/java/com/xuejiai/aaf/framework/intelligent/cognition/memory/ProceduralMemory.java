/**
 * 程序化记忆实体。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import jakarta.persistence.*;

import com.xuejiai.aaf.common.entity.BaseEntity;

import lombok.Getter;
import lombok.Setter;

/**
 * 程序化记忆：经验蒸馏、SOP 记忆、技能记忆。
 * 参考 ReMe，存储"如何做"的经验，以 Markdown 格式持久化。
 */
@Getter
@Setter
@Entity
@Table(name = "ai_procedural_memory", indexes = {
    @Index(columnList = "userId,category"),
    @Index(columnList = "taskType")
})
public class ProceduralMemory extends BaseEntity {

    /** 所属用户（null 表示全局共享） */
    private Long userId;

    /** 任务类型（如 "code_review", "bug_fix", "data_analysis"） */
    @Column(nullable = false, length = 64)
    private String taskType;

    /** 分类 */
    @Column(length = 64)
    private String category;

    /** 标题 */
    @Column(nullable = false, length = 256)
    private String title;

    /** 经验内容（Markdown 格式的 SOP/步骤） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 成功次数（用于评估经验质量） */
    @Column(nullable = false)
    private Integer successCount = 0;

    /** 使用次数 */
    @Column(nullable = false)
    private Integer useCount = 0;

    /** 质量评分 0.0~1.0 */
    @Column(nullable = false)
    private Double qualityScore = 0.5;
}
