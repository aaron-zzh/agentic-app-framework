package com.xuejiai.aaf.module.system.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

/** 用户-角色关联。 */
@Getter
@Setter
@Entity
@Table(
        name = "sys_user_role",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "role_id"}))
@SQLDelete(
        sql =
                "UPDATE sys_user_role SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id"
                        + " = ?")
public class UserRole extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;
}
