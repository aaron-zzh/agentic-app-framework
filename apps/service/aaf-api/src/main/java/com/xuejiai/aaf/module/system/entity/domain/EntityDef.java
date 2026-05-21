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

/** 实体定义。 */
@Getter
@Setter
@Entity
@Table(name = "sys_entity_def")
@SQLDelete(
        sql =
                "UPDATE sys_entity_def SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class EntityDef extends BaseEntity {

    /** 实体标识（唯一） */
    @Column(name = "slug", nullable = false, length = 100)
    private String slug;

    /** 实体配置（JSONB，含 fields 数组） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", nullable = false, columnDefinition = "jsonb")
    private String config;

    /** 是否内置 */
    @Column(name = "builtin", nullable = false)
    private Boolean builtin = false;

    /** 是否启用 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
