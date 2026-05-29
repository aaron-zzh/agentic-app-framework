package com.xuejiai.aaf.module.company.ops.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

/** 运营任务定义 */
@Getter
@Setter
@Entity
@Table(name = "company_ops_task")
@SQLDelete(sql = "UPDATE company_ops_task SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class OpsTask extends BaseEntity {

    /** 任务名称 */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** 任务描述 */
    @Column(name = "description", length = 512)
    private String description;

    /** 分类（REPORT/SYNC/CHECK/NOTIFY/CUSTOM） */
    @Column(name = "category", nullable = false, length = 32)
    private String category;

    /** Cron 表达式 */
    @Column(name = "cron_expr", length = 64)
    private String cronExpr;

    /** 触发方式（CRON/EVENT/MANUAL） */
    @Column(name = "trigger_type", nullable = false, length = 16)
    private String triggerType;

    /** 执行 Agent ID */
    @Column(name = "agent_id")
    private Long agentId;

    /** 任务配置（JSON） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private String config;

    /** 是否启用 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
