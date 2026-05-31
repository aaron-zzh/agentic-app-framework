package com.xuejiai.aaf.module.system.permission.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 功能权限码。菜单可引用权限码，但权限码本身不承载导航结构。 */
@Getter
@Setter
@Entity
@Table(name = "sys_permission_code")
@SQLDelete(
        sql =
                "UPDATE sys_permission_code SET deleted = true, delete_time = CURRENT_TIMESTAMP"
                        + " WHERE id = ?")
public class PermissionCode extends BaseEntity {

    /** 权限名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 三段式权限码：模块:资源:动作 */
    @Column(name = "code", nullable = false, unique = true, length = 120)
    private String code;

    /** 模块标识，如 system/ai/kb */
    @Column(name = "module", nullable = false, length = 50)
    private String module;

    /** 资源标识，如 user/role/document */
    @Column(name = "resource", nullable = false, length = 50)
    private String resource;

    /** 动作标识，如 read/create/update/delete/export */
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    /** 0 正常 / 1 禁用 */
    @Column(name = "status", nullable = false)
    private Integer status = 0;
}
