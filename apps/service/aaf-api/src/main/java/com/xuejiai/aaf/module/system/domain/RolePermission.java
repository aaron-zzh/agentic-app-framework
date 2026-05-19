package com.xuejiai.aaf.module.system.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

/** 角色-权限关联。 */
@Getter
@Setter
@Entity
@Table(
        name = "sys_role_permission",
        uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "permission_id"}))
@SQLDelete(
        sql =
                "UPDATE sys_role_permission SET deleted = true, delete_time = CURRENT_TIMESTAMP"
                        + " WHERE id = ?")
public class RolePermission extends BaseEntity {

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "permission_id", nullable = false)
    private Long permissionId;
}
