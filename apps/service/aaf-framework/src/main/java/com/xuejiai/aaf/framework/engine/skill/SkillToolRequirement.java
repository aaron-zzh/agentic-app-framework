package com.xuejiai.aaf.framework.engine.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** SkillVersion 声明的工具要求；仅用于收窄 AAF 已授权工具。 */
@Getter
@Setter
@Entity
@Table(name = "ai_skill_tool_requirement")
public class SkillToolRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_version_id", nullable = false)
    private Long skillVersionId;

    @Column(name = "tool_id", nullable = false, length = 128)
    private String toolId;

    @Column(name = "tool_version")
    private Long toolVersion;

    @Column(name = "tool_name", nullable = false, length = 128)
    private String toolName;

    @Column(nullable = false)
    private Boolean required = false;

    @Column(name = "usage_purpose", nullable = false, length = 512)
    private String usagePurpose;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 100;
}
