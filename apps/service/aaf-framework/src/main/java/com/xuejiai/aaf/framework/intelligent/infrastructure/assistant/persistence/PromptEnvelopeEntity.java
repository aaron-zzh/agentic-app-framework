package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptEnvelope;

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
        name = "ai_prompt_envelope",
        uniqueConstraints =
                @UniqueConstraint(columnNames = {"tenant_id", "execution_id", "envelope_seq"}))
public class PromptEnvelopeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "task_id", length = 128)
    private String taskId;

    @Column(name = "execution_id", nullable = false, length = 128)
    private String executionId;

    @Column(name = "envelope_seq", nullable = false)
    private Integer envelopeSeq;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Column(name = "prompt_sha256", nullable = false, length = 64)
    private String promptSha256;

    @Column(name = "trigger_kind", nullable = false, length = 32)
    private String triggerKind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "envelope_payload", nullable = false, columnDefinition = "jsonb")
    private PromptEnvelope envelope;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
