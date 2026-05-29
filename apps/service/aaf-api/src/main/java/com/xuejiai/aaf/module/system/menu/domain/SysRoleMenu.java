package com.xuejiai.aaf.module.system.menu.domain;

import java.io.Serializable;

import jakarta.persistence.*;
import lombok.*;

/**
 * 角色-菜单关联
 */
@Getter
@Setter
@Entity
@Table(name = "sys_role_menu")
@IdClass(SysRoleMenu.Id.class)
public class SysRoleMenu {

    @jakarta.persistence.Id
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @jakarta.persistence.Id
    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {
        private Long roleId;
        private Long menuId;
    }
}
