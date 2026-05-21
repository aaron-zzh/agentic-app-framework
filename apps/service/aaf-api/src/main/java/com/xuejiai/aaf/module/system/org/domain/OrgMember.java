package com.xuejiai.aaf.module.system.org.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 组织成员。 */
@Getter
@Setter
@Entity
@Table(name = "sys_org_member")
@SQLDelete(
        sql =
                "UPDATE sys_org_member SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id"
                        + " = ?")
public class OrgMember extends BaseEntity {

    /** 所属组织 ID（覆盖 BaseEntity 的 orgId，此处为业务字段） */
    @Column(name = "org_id", nullable = false)
    private Long orgId;

    /** 用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** owner 所有者 / admin 管理员 / member 成员 */
    @Column(name = "role", nullable = false, length = 20)
    private String role;
}
