package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** 系统级 Skill 绑定持久化实体。 */
@Getter
@Setter
@Entity
@Table(
        name = "ai_system_skill_binding",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_ai_system_skill_binding_skill",
                        columnNames = {"skill_id"}))
public class SystemSkillBindingEntity extends BaseEntity {

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "activation_mode", nullable = false, length = 16)
    private String activationMode;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 100;
}
