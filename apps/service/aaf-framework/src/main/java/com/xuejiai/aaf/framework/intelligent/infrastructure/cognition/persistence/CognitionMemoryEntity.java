package com.xuejiai.aaf.framework.intelligent.infrastructure.cognition.persistence;

import java.time.Instant;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Cognition 长期记忆唯一事实实体。 */
@Getter
@Setter
@Entity
@Table(name = "ai_cognition_memory")
public class CognitionMemoryEntity {
    @Id
    @Column(name = "memory_id", length = 64)
    private String memoryId;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "subject_kind", nullable = false, length = 16)
    private String subjectKind;

    @Column(name = "subject_id", nullable = false, length = 128)
    private String subjectId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "redacted_summary", nullable = false, length = 256)
    private String redactedSummary;

    @Column(nullable = false)
    private Double importance;

    @Column(nullable = false)
    private Double confidence;

    @Column(nullable = false, length = 16)
    private String privacy;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> tags;

    @Column(columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "forgotten_at")
    private Instant forgottenAt;
}
