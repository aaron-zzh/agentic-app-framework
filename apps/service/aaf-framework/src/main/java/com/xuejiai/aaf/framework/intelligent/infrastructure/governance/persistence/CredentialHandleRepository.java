package com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CredentialHandleRepository extends JpaRepository<CredentialHandleEntity, String> {

    @Query(
            """
            SELECT h FROM CredentialHandleEntity h
            WHERE h.tenantId = :tenantId AND h.userId = :userId
              AND h.connectorId = :connectorId AND h.revokedAt IS NULL
              AND h.expiresAt > :at
            ORDER BY h.createdAt DESC
            """)
    List<CredentialHandleEntity> findActive(
            String tenantId, String userId, String connectorId, Instant at);

    @Modifying
    @Query(
            """
            UPDATE CredentialHandleEntity h SET h.revokedAt = :at
            WHERE h.tenantId = :tenantId AND h.userId = :userId
              AND h.handleId = :handleId AND h.revokedAt IS NULL
            """)
    int revoke(String tenantId, String userId, String handleId, Instant at);
}
