package com.xuejiai.aaf.module.system.permission.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 权限点（菜单/按钮/接口）。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity(name = "MenuPermission")
@Table(name = "sys_menu_permission")
@SQLDelete(
        sql =
                "UPDATE sys_menu_permission SET deleted = true, delete_time = CURRENT_TIMESTAMP"
                        + " WHERE id = ?")
public class Permission extends BaseEntity {

    /** 权限名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 权限编码，唯一标识 */
    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    /** 权限类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PermissionType type;

    /** 父级 ID，顶级为 0 */
    @Column(name = "parent_id", nullable = false)
    private Long parentId = 0L;

    /** 路由路径 */
    @Column(name = "path", length = 255)
    private String path;

    /** 图标 */
    @Column(name = "icon", length = 100)
    private String icon;

    /** 排序号 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** 0 正常 / 1 禁用 */
    @Column(name = "status", nullable = false)
    private Integer status = 0;
}
