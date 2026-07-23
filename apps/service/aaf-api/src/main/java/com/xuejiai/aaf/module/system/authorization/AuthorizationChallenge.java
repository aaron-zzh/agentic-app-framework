package com.xuejiai.aaf.module.system.authorization;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Check;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 持久化的一次性授权 challenge。 */
@Getter
@Setter
@Entity
@Table(
        name = "sys_authorization_challenge",
        indexes =
                @Index(
                        name = "idx_authorization_challenge_subject_status",
                        columnList = "subject_id,status,expires_at"))
@Check(constraints = "status in ('PENDING','APPROVED','CONSUMED')")
public class AuthorizationChallenge {

    @Id private UUID id;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "workspace_id")
    private Long workspaceId;

    @Column(nullable = false, length = 128)
    private String resource;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "object_id", length = 128)
    private String objectId;

    @Column(name = "request_digest", nullable = false, length = 64)
    private String requestDigest;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "policy_version", nullable = false)
    private Long policyVersion;

    @Column(name = "snapshot_version", nullable = false, length = 64)
    private String snapshotVersion;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "create_time", nullable = false)
    private Instant createTime;
}
