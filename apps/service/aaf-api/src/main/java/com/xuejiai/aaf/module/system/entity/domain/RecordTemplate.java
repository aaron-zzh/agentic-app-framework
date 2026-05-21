package com.xuejiai.aaf.module.system.entity.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 记录模板。 */
@Getter
@Setter
@Entity
@Table(name = "sys_record_template")
@SQLDelete(
        sql =
                "UPDATE sys_record_template SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class RecordTemplate extends BaseEntity {

    /** 关联实体标识 */
    @Column(name = "entity_slug", nullable = false, length = 100)
    private String entitySlug;

    /** 模板名称 */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** 模板字段值（JSONB） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_values", nullable = false, columnDefinition = "jsonb")
    private String fieldValues;

    /** 是否共享（团队可见） */
    @Column(name = "is_shared", nullable = false)
    private Boolean isShared = false;

    /** 是否默认模板 */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;
}
