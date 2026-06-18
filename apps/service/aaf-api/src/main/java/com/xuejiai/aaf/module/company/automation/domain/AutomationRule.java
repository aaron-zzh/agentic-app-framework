package com.xuejiai.aaf.module.company.automation.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** AI 自动化规则 */
@Getter
@Setter
@Entity(name = "CompanyAutomationRule")
@Table(name = "company_automation_rule")
@SQLDelete(
        sql =
                "UPDATE company_automation_rule SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AutomationRule extends BaseEntity {

    /** 规则名称 */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** 触发事件（如 OKR_PROGRESS_UPDATE/RESOURCE_LOW/TASK_FAILED） */
    @Column(name = "trigger_event", nullable = false, length = 64)
    private String triggerEvent;

    /** 条件表达式（JSON） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions", columnDefinition = "jsonb")
    private String conditions;

    /** 动作类型（NOTIFY/CREATE_TASK/CALL_AGENT/WEBHOOK） */
    @Column(name = "action_type", nullable = false, length = 32)
    private String actionType;

    /** 动作配置（JSON） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_config", columnDefinition = "jsonb")
    private String actionConfig;

    /** 关联 Agent ID */
    @Column(name = "agent_id")
    private Long agentId;

    /** 是否启用 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
