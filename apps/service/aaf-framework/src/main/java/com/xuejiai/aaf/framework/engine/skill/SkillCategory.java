package com.xuejiai.aaf.framework.engine.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Skill 目录分类，属于平台全局受控词表。 */
@Getter
@Setter
@Entity
@Table(name = "ai_skill_category")
public class SkillCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 100;
}
