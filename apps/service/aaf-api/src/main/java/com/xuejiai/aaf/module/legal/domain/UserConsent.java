package com.xuejiai.aaf.module.legal.domain;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户对法律文档（服务条款 / 隐私政策）的同意快照。
 *
 * <p>每次同意都新增一条记录，包含同意时刻、客户端 IP、来源应用、文档版本号，便于合规审计追溯。
 *
 * <p>对应 {@code sys_user_consent} 表，软删除继承自 {@link BaseEntity}。
 *
 * @author AaronZZH &amp; Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_user_consent")
public class UserConsent extends BaseEntity {

    /** 用户 ID（sys_user.id） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 文档 ID（doc_document.id），同意时刻锁定的具体版本 */
    @Column(name = "document_id", nullable = false)
    private Long documentId;

    /** 文档类型：legal-terms / legal-privacy（冗余，便于按类型查询） */
    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    /** 文档版本号（取自 front_matter.version，缺省回退到文档 update_time 字符串） */
    @Column(name = "document_version", nullable = false, length = 50)
    private String documentVersion;

    /** 同意时间 */
    @Column(name = "consent_time", nullable = false)
    private LocalDateTime consentTime;

    /** 同意时客户端 IP（合规审计用） */
    @Column(name = "consent_ip", length = 50)
    private String consentIp;

    /** 来源应用：web / uniapp / api 等 */
    @Column(name = "source_app", length = 32)
    private String sourceApp;
}
