package com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizationGrant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_authorization_grant")
public class AuthorizationGrantEntity {
    @Id
    @Column(name = "grant_id", length = 64)
    private String grantId;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(nullable = false, length = 256)
    private String action;

    @Column(nullable = false, length = 256)
    private String resource;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "grant_payload", nullable = false, columnDefinition = "jsonb")
    private AuthorizationGrant grant;
}
