package com.xuejiai.aaf.module.system.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

/** 实体权限。 */
@Getter
@Setter
@Entity
@Table(name = "sys_permission")
@SQLDelete(
        sql =
                "UPDATE sys_permission SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id"
                        + " = ?")
public class Permission extends BaseEntity {

    /** 实体标识 */
    @Column(name = "entity_slug", nullable = false, length = 100)
    private String entitySlug;

    /** 操作类型：read/create/update/delete */
    @Column(name = "action", nullable = false, length = 20)
    private String action;

    /** 字段级权限，JSONB 格式 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_access", columnDefinition = "jsonb")
    private String fieldAccess;
}
