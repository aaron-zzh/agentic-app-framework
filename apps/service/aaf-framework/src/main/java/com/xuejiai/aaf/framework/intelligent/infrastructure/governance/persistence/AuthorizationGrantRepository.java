package com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AuthorizationGrantRepository
        extends JpaRepository<AuthorizationGrantEntity, String> {

    @Query(
            """
            SELECT g FROM AuthorizationGrantEntity g
            WHERE g.tenantId = :tenantId AND g.taskId = :taskId
              AND g.action = :action AND g.revokedAt IS NULL AND g.expiresAt > :at
            """)
    List<AuthorizationGrantEntity> findActive(
            String tenantId, String taskId, String action, Instant at);

    List<AuthorizationGrantEntity> findByTenantIdAndTaskIdOrderByExpiresAtDesc(
            String tenantId, String taskId);

    @Modifying
    @Query(
            """
            UPDATE AuthorizationGrantEntity g SET g.revokedAt = :at
            WHERE g.tenantId = :tenantId AND g.grantId = :grantId AND g.revokedAt IS NULL
            """)
    int revoke(String tenantId, String grantId, Instant at);
}
