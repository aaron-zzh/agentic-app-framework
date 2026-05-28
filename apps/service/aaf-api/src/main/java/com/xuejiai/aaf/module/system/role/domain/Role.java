package com.xuejiai.aaf.module.system.role.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统角色。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_role")
@SQLDelete(sql = "UPDATE sys_role SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class Role extends BaseEntity {

    /** 角色编码，唯一标识 */
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    /** 角色名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 角色描述 */
    @Column(name = "description", length = 500)
    private String description;

    /** 0 正常 / 1 禁用 */
    @Column(name = "status", nullable = false)
    private Integer status = 0;
}
