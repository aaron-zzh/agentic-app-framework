package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ClarificationRequest;

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
@Table(name = "ai_clarification_request")
public class ClarificationRequestEntity {
    @Id
    @Column(name = "request_id", length = 128)
    private String requestId;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(name = "execution_id", nullable = false, length = 128)
    private String executionId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "deadline", nullable = false)
    private Instant deadline;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", nullable = false, columnDefinition = "jsonb")
    private ClarificationRequest request;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
