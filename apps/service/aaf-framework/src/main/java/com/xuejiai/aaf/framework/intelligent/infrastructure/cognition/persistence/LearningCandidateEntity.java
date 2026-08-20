package com.xuejiai.aaf.framework.intelligent.infrastructure.cognition.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.cognition.learning.LearningCandidate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "ai_learning_candidate",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "source_event_id"}))
public class LearningCandidateEntity {
    @Id
    @Column(name = "candidate_id", length = 128)
    private String candidateId;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(name = "execution_id", nullable = false, length = 128)
    private String executionId;

    @Column(name = "run_id", nullable = false, length = 128)
    private String runId;

    @Column(name = "session_id", nullable = false, length = 128)
    private String sessionId;

    @Column(name = "source_event_id", nullable = false, length = 128)
    private String sourceEventId;

    @Column(name = "source_event_offset", nullable = false)
    private Long sourceEventOffset;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_payload", nullable = false, columnDefinition = "jsonb")
    private LearningCandidate candidate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;
}
