package com.xuejiai.aaf.module.system.mail.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 邮件模板。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_mail_template")
@SQLDelete(
        sql =
                "UPDATE sys_mail_template SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class MailTemplate extends BaseEntity {

    /** 模板编码，唯一标识 */
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    /** 模板名称 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 邮件主题 */
    @Column(nullable = false, length = 200)
    private String subject;

    /** 邮件内容（支持 HTML） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 关联邮件账号 ID */
    @Column(nullable = false)
    private Long accountId;

    /** 模板参数（JSON 数组，如 ["username","code"]） */
    @Column(length = 500)
    private String params;

    /** 状态：1=启用 0=禁用 */
    @Column(nullable = false)
    private Short status = 1;
}
