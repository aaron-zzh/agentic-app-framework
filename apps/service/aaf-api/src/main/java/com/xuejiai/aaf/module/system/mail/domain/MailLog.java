package com.xuejiai.aaf.module.system.mail.domain;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 邮件发送日志。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_mail_log")
public class MailLog extends BaseEntity {

    /** 关联模板 ID */
    @Column(nullable = false)
    private Long templateId;

    /** 收件人地址 */
    @Column(nullable = false, length = 200)
    private String toAddress;

    /** 邮件主题 */
    @Column(nullable = false, length = 200)
    private String subject;

    /** 邮件内容 */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 发送状态：0=发送中 1=成功 2=失败 */
    @Column(nullable = false)
    private Short sendStatus = 0;

    /** 发送时间 */
    private LocalDateTime sendTime;

    /** 错误信息 */
    @Column(length = 500)
    private String errorMessage;
}
