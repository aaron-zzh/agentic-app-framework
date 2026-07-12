package com.xuejiai.aaf.module.system.org.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 工作区成员——记录用户与工作区的归属关系。
 *
 * <p>不设角色字段：工作区权限模型无独立角色层级，管理权限由 {@link Workspace#getCreateBy()} 判断，
 * 本表只记录"谁在哪个工作区里"这一归属关系。组织成员不自动加入工作区，需显式邀请/加入。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_workspace_member")
@SQLDelete(
        sql =
                "UPDATE sys_workspace_member SET deleted = true, delete_time = CURRENT_TIMESTAMP"
                        + " WHERE id = ?")
public class WorkspaceMember extends BaseEntity {

    /** 所属工作区 ID（覆盖 BaseEntity 的 workspaceId，此处为业务字段） */
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    /** 用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;
}
