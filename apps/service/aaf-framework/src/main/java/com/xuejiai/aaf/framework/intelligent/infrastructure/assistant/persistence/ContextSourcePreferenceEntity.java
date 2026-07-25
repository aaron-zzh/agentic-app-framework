package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;

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
@Table(name = "ai_context_source_preference",
        uniqueConstraints = @UniqueConstraint(columnNames = {
                "tenant_id", "user_id", "assistant_id", "source_type", "source_key"}))
public class ContextSourcePreferenceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;
    @Column(name = "assistant_id", nullable = false, length = 128)
    private String assistantId;
    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;
    @Column(name = "source_key", nullable = false, length = 256)
    private String sourceKey;
    @Column(nullable = false, length = 16)
    private String disposition;
    @Column(nullable = false, length = 512)
    private String reason;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
