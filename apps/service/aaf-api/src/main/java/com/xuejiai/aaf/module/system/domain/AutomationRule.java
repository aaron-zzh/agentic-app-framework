package com.xuejiai.aaf.module.system.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 自动化规则。 */
@Getter
@Setter
@Entity
@Table(name = "sys_automation_rule")
public class AutomationRule extends BaseEntity {

    /** 规则名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 关联实体标识 */
    @Column(name = "entity_slug", nullable = false, length = 100)
    private String entitySlug;

    /** 触发器类型：on_create/on_update/field_change/schedule/delay */
    @Column(name = "trigger_type", nullable = false, length = 50)
    private String triggerType;

    /** 触发条件（JSON） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions", columnDefinition = "jsonb")
    private String conditions;

    /** 执行动作列表（JSON） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actions", nullable = false, columnDefinition = "jsonb")
    private String actions;

    /** 是否启用 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
