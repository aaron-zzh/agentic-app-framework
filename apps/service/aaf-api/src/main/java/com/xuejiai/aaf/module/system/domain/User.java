package com.xuejiai.aaf.module.system.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 系统用户。 */
@Getter
@Setter
@Entity
@Table(name = "sys_user")
public class User extends BaseEntity {

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password", nullable = false, length = 200)
    private String password;

    @Column(name = "nickname", length = 100)
    private String nickname;

    /** 1 启用 / 0 禁用 */
    @Column(name = "status", nullable = false, columnDefinition = "smallint")
    private Short status = 1;
}
