package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_delegated_task", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "task_id"}))
public class DelegatedTaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;
    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;
    @Column(name = "conversation_id", nullable = false, length = 128)
    private String conversationId;
    @Column(name = "execution_id", nullable = false, length = 128)
    private String executionId;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "owner_kind", nullable = false, length = 16)
    private String ownerKind;
    @Column(name = "owner_id", nullable = false, length = 128)
    private String ownerId;
    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;
    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;
    @Column(name = "lease_until")
    private Instant leaseUntil;
    @Column(name = "fencing_token", nullable = false)
    private Long fencingToken;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "task_payload", nullable = false, columnDefinition = "jsonb")
    private DelegatedTask task;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "command_payload", nullable = false, columnDefinition = "jsonb")
    private AssistantCommand command;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;
}
