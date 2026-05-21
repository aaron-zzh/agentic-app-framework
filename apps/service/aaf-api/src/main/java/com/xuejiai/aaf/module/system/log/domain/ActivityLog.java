package com.xuejiai.aaf.module.system.log.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 活动日志。 */
@Getter
@Setter
@Entity
@Table(name = "sys_activity_log")
@SQLDelete(
        sql =
                "UPDATE sys_activity_log SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ActivityLog extends BaseEntity {

    /** 关联实体类型 */
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    /** 关联实体 ID */
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** 操作类型（如 created / updated / deleted） */
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    /** 变更详情（JSON） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", columnDefinition = "jsonb")
    private String changes;
}
