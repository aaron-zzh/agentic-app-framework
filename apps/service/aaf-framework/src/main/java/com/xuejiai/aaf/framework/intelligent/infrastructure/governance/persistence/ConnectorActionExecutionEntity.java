package com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_connector_action_execution")
public class ConnectorActionExecutionEntity {
    @Id
    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;
    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;
    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;
    @Column(name = "execution_id", nullable = false, length = 128)
    private String executionId;
    @Column(name = "connector_id", nullable = false, length = 256)
    private String connectorId;
    @Column(name = "action_name", nullable = false, length = 120)
    private String actionName;
    @Column(name = "request_digest", nullable = false, length = 64)
    private String requestDigest;
    @Column(name = "provider_idempotency_key", nullable = false, length = 128)
    private String providerIdempotencyKey;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(columnDefinition = "TEXT")
    private String result;
    @Column(nullable = false)
    private Integer attempts;
    @Column(name = "last_error", length = 1000)
    private String lastError;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
