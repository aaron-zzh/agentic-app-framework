package com.xuejiai.aaf.module.system.entity.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.org.OrgIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 实体定义。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_entity_def")
@SQLDelete(
        sql =
                "UPDATE sys_entity_def SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
@OrgIgnore
public class EntityDef extends BaseEntity {

    /** 实体标识（唯一） */
    @Column(name = "slug", nullable = false, length = 100)
    private String slug;

    /** 实体 UI 元数据配置（JSONB） */
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
