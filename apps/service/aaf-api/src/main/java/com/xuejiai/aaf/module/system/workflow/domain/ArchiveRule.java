package com.xuejiai.aaf.module.system.workflow.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 数据归档规则
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_archive_rule")
@SQLDelete(
        sql =
                "UPDATE sys_archive_rule SET deleted = true, delete_time = CURRENT_TIMESTAMP"
                        + " WHERE id = ?")
public class ArchiveRule extends BaseEntity {

    /** 实体标识 */
    @Column(name = "entity_slug", nullable = false, length = 100)
    private String entitySlug;

    /** 归档条件，JSONB 格式 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition", nullable = false, columnDefinition = "jsonb")
    private String condition;

    /** 满足条件后等待天数 */
    @Column(name = "after_days", nullable = false)
    private Integer afterDays = 90;

    /** 是否启用 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
