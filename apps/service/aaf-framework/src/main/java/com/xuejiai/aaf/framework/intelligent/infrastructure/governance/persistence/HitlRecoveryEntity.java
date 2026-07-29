package com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_hitl_recovery")
public class HitlRecoveryEntity {
    @Id
    @Column(name = "approval_id", length = 64)
    private String approvalId;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private Integer attempts;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "command_payload", nullable = false, columnDefinition = "jsonb")
    private AssistantCommand command;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
