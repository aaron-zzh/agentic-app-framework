package com.xuejiai.aaf.framework.engine.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** SkillVersion 要求的模型能力。 */
@Getter
@Setter
@Entity
@Table(name = "ai_skill_model_requirement")
public class SkillModelRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_version_id", nullable = false)
    private Long skillVersionId;

    @Column(nullable = false, length = 32)
    private String capability;

    @Column(nullable = false)
    private Boolean required = true;

    @Column(name = "minimum_context_tokens")
    private Integer minimumContextTokens;

    @Column(nullable = false, length = 512)
    private String rationale;
}
