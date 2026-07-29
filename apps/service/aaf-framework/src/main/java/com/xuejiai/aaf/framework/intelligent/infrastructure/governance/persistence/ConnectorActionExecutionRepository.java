package com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ConnectorActionExecutionRepository
        extends JpaRepository<ConnectorActionExecutionEntity, String> {

    @Modifying
    @Transactional
    @Query(
            value =
                    """
                    INSERT INTO ai_connector_action_execution (
                        idempotency_key, tenant_id, user_id, task_id, execution_id,
                        connector_id, action_name, request_digest, provider_idempotency_key,
                        status, result, attempts, created_at, updated_at)
                    VALUES (
                        :key, :tenantId, :userId, :taskId, :executionId,
                        :connectorId, :actionName, :requestDigest, :providerKey,
                        'PENDING', NULL, 0, :now, :now)
                    ON CONFLICT (idempotency_key) DO NOTHING
                    """,
            nativeQuery = true)
    int claim(
            String key,
            String tenantId,
            String userId,
            String taskId,
            String executionId,
            String connectorId,
            String actionName,
            String requestDigest,
            String providerKey,
            Instant now);

    @Modifying
    @Transactional
    @Query(
            """
            UPDATE ConnectorActionExecutionEntity e
            SET e.attempts = e.attempts + 1, e.lastError = NULL, e.updatedAt = :now
            WHERE e.idempotencyKey = :key AND e.status = 'PENDING'
            """)
    int markAttempt(String key, Instant now);

    @Modifying
    @Transactional
    @Query(
            """
            UPDATE ConnectorActionExecutionEntity e
            SET e.status = 'SUCCEEDED', e.result = :result,
                e.lastError = NULL, e.updatedAt = :now
            WHERE e.idempotencyKey = :key AND e.status = 'PENDING'
            """)
    int complete(String key, String result, Instant now);

    @Modifying
    @Transactional
    @Query(
            """
            UPDATE ConnectorActionExecutionEntity e
            SET e.lastError = :error, e.updatedAt = :now
            WHERE e.idempotencyKey = :key AND e.status = 'PENDING'
            """)
    int recordFailure(String key, String error, Instant now);
}
