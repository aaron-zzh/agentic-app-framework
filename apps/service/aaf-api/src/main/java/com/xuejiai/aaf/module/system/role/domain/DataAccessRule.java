package com.xuejiai.aaf.module.system.role.domain;

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
 * 行级数据权限规则。
 *
 * <p>规则定义本身是全局配置（{@code org_id} 恒为 NULL，参见 {@code db/seed/v13__access_rules.sql}）， 语义上不属于任何组织，与
 * {@code Role}/{@code RolePermission}/{@code PermissionCode} 同类， 必须标注 {@link OrgIgnore}，否则套用 {@code
 * orgFilter}（{@code WHERE org_id = ?}）后 {@code NULL = :orgId} 恒为 false，规则查询静默返回空，L3
 * 行级权限全部失效（放行而非拒绝，风险更高）。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_data_access_rule")
@OrgIgnore
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
