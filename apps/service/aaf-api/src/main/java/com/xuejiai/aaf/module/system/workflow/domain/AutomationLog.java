package com.xuejiai.aaf.module.system.workflow.domain;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 自动化执行日志。 */
@Getter
@Setter
@Entity
@Table(name = "sys_automation_log")
public class AutomationLog extends BaseEntity {

    /** 规则 ID */
    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    /** 触发器类型 */
    @Column(name = "trigger_type", nullable = false, length = 50)
    private String triggerType;

    /** 关联实体类型 */
    @Column(name = "entity_type", length = 100)
    private String entityType;

    /** 关联实体 ID */
    @Column(name = "entity_id")
    private Long entityId;

    /** 执行状态：success/failed/skipped */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 错误信息 */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 执行时间 */
    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;
}
