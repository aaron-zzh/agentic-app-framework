package com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;

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
@Table(name = "ai_tool_invocation_receipt")
public class ToolInvocationReceiptEntity {
    @Id
    @Column(name = "receipt_key", length = 128)
    private String receiptKey;
    @Column(name = "request_digest", nullable = false, length = 64)
    private String requestDigest;
    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;
    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;
    @Column(name = "execution_id", nullable = false, length = 128)
    private String executionId;
    @Column(name = "fencing_token", nullable = false)
    private Long fencingToken;
    @Column(name = "tool_id", nullable = false, length = 256)
    private String toolId;
    @Column(name = "action_key", nullable = false, length = 256)
    private String actionKey;
    @Column(nullable = false, length = 16)
    private String status;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_payload", columnDefinition = "jsonb")
    private ToolInvocationResult result;
    @Column(name = "last_error", length = 1000)
    private String lastError;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;
}
