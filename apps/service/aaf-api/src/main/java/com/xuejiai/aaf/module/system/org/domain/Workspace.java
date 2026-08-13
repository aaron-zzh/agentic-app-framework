package com.xuejiai.aaf.module.system.org.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 工作区——组织内部的协作空间划分。
 *
 * <p>用户在所属组织下可自建多个工作区，权限模型不设独立角色层级：{@code ownerId} 即该工作区的管理者， 拥有邀请/移除成员、改名、删除工作区等全部管理权限。详见设计文档
 * {@code docs/design/apps/service/workspace-isolation.md}。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_workspace")
@SQLDelete(
        sql =
                "UPDATE sys_workspace SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id"
                        + " = ?")
public class Workspace extends BaseEntity {

    /** 工作区名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 工作区唯一标识（URL 友好，组织内唯一） */
    @Column(name = "slug", nullable = false, length = 100)
    private String slug;
}
