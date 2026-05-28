package com.xuejiai.aaf.module.system.role.domain;

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
 * 行级数据权限规则。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_data_access_rule")
@SQLDelete(
        sql =
                "UPDATE sys_data_access_rule SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class DataAccessRule extends BaseEntity {

    /** 实体标识 */
    @Column(name = "entity_slug", nullable = false, length = 100)
    private String entitySlug;

    /** 适用角色编码数组，JSONB 格式：["user","editor"] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "roles", nullable = false, columnDefinition = "jsonb")
    private String roles;

    /** 条件表达式，JSONB 格式 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition", nullable = false, columnDefinition = "jsonb")
    private String condition;

    /** 效果：allow / deny */
    @Column(name = "effect", nullable = false, length = 10)
    private String effect = "allow";
}
