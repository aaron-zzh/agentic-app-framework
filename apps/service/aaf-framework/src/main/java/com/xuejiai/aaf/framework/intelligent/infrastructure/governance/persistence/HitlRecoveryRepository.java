package com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface HitlRecoveryRepository extends JpaRepository<HitlRecoveryEntity, String> {

    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO ai_hitl_recovery (
                        approval_id, tenant_id, task_id, status, attempts,
                        command_payload, created_at, updated_at, version)
                    VALUES (
                        :approvalId, :tenantId, :taskId, 'PENDING', 0,
                        cast(:commandPayload AS jsonb), :at, :at, 0)
                    ON CONFLICT (approval_id) DO NOTHING
                    """,
            nativeQuery = true)
    int schedule(
            String approvalId, String tenantId, String taskId, String commandPayload, Instant at);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT r FROM HitlRecoveryEntity r
            WHERE r.tenantId = :tenantId AND r.approvalId = :approvalId
            """)
    Optional<HitlRecoveryEntity> findForUpdate(String tenantId, String approvalId);
}
