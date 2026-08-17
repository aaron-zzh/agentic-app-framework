package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionProfileSnapshot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "ai_execution_profile_snapshot",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "execution_id"}))
public class ExecutionProfileSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(name = "execution_id", nullable = false, length = 128)
    private String executionId;

    @Column(name = "assistant_id", nullable = false, length = 128)
    private String assistantId;

    @Column(name = "assistant_revision", nullable = false)
    private Long assistantRevision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_payload", nullable = false, columnDefinition = "jsonb")
    private ExecutionProfileSnapshot snapshot;

    @Column(name = "frozen_at", nullable = false)
    private Instant frozenAt;
}
