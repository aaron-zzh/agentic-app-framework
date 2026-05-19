package com.xuejiai.aaf.module.system.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 审计日志（不可变记录，不继承 BaseEntity）。 */
@Getter
@Setter
@NoArgsConstructor
@Immutable
@Entity
@Table(name = "sys_audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "workspace_id")
    private Long workspaceId;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "user_id")
    private Long userId;

    /** 变更详情（JSON） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", columnDefinition = "jsonb")
    private String changes;

    @Column(name = "ip", length = 45)
    private String ip;

    /** 当前记录内容 SHA-256 哈希 */
    @Column(name = "hash", length = 64)
    private String hash;

    /** 前一条记录的哈希，形成链式校验 */
    @Column(name = "previous_hash", length = 64)
    private String previousHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
