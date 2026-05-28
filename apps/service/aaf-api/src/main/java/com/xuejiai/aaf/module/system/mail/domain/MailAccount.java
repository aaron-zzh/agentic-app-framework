package com.xuejiai.aaf.module.system.mail.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 邮件账号配置。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_mail_account")
@SQLDelete(
        sql =
                "UPDATE sys_mail_account SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class MailAccount extends BaseEntity {

    /** 账号名称 */
    @Column(nullable = false, length = 100)
    private String name;

    /** SMTP 主机 */
    @Column(nullable = false, length = 200)
    private String host;

    /** SMTP 端口 */
    @Column(nullable = false)
    private Integer port;

    /** 登录用户名 */
    @Column(nullable = false, length = 200)
    private String username;

    /** 登录密码 */
    @Column(nullable = false, length = 200)
    private String password;

    /** 是否启用 SSL */
    @Column(nullable = false)
    private Boolean sslEnabled = false;

    /** 发件人地址 */
    @Column(nullable = false, length = 200)
    private String fromAddress;

    /** 状态：1=启用 0=禁用 */
    @Column(nullable = false)
    private Short status = 1;
}
