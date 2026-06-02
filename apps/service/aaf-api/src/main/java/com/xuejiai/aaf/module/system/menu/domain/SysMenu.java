package com.xuejiai.aaf.module.system.menu.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 系统菜单 */
@Getter
@Setter
@Entity
@Table(name = "sys_menu")
@SQLDelete(sql = "UPDATE sys_menu SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class SysMenu extends BaseEntity {

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "title", nullable = false, length = 50)
    private String title;

    @Column(name = "path", length = 200)
    private String path;

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "visible", nullable = false)
    private Boolean visible = true;

    @Column(name = "menu_type", nullable = false, length = 20)
    private String menuType = "MENU";

    @Column(name = "permission_code", length = 120)
    private String permissionCode;
}
