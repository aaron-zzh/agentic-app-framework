package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest;

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
        name = "ai_effective_context_manifest",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "task_id"}))
public class EffectiveContextManifestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "manifest_payload", nullable = false, columnDefinition = "jsonb")
    private EffectiveContextManifest manifest;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
