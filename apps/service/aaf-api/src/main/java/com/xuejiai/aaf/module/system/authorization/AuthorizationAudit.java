package com.xuejiai.aaf.module.system.authorization;

import java.time.Instant;
import java.util.UUID;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 统一授权与策略发布审计记录。
 *
 * <p>标注 {@link com.xuejiai.aaf.framework.org.OrgIgnore}：与 {@link AccessPolicy} 同理，租户边界由自身的 {@code
 * tenantId} 字段表达，不套用 {@code org_id} 过滤。
 */
@Getter
@Setter
@Entity
@Table(
        name = "sys_authorization_audit",
        indexes = {
            @Index(
                    name = "idx_authorization_audit_subject_time",
                    columnList = "subject_id,occurred_at"),
            @Index(name = "idx_authorization_audit_policy", columnList = "policy_id,policy_version")
        })
@com.xuejiai.aaf.framework.org.OrgIgnore
public class AuthorizationAudit extends BaseEntity {

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "subject_id")
    private Long subjectId;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(length = 128)
    private String resource;

    @Column(length = 64)
    private String action;

    @Column(name = "object_id", length = 128)
    private String objectId;

    @Column(length = 32)
    private String layer;

    @Column(nullable = false, length = 32)
    private String effect;

    @Column(name = "policy_id")
    private Long policyId;

    @Column(name = "policy_version")
    private Long policyVersion;

    @Column(name = "snapshot_version", length = 64)
    private String snapshotVersion;

    @Column(nullable = false)
    private Boolean shadow = false;

    @Column(name = "challenge_id")
    private UUID challengeId;

    @Column(length = 512)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
