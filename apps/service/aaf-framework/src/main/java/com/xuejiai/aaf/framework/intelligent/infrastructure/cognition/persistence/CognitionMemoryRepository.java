package com.xuejiai.aaf.framework.intelligent.infrastructure.cognition.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CognitionMemoryRepository extends JpaRepository<CognitionMemoryEntity, String> {

    @Query("""
            SELECT m FROM CognitionMemoryEntity m
            WHERE m.tenantId = :tenantId AND m.subjectKind = :subjectKind
              AND m.subjectId = :subjectId AND m.forgottenAt IS NULL
              AND (m.expiresAt IS NULL OR m.expiresAt > :at)
            ORDER BY m.importance DESC, m.createdAt DESC
            """)
    List<CognitionMemoryEntity> findActive(
            String tenantId, String subjectKind, String subjectId, Instant at);

    @Query(value = """
            SELECT * FROM ai_cognition_memory
            WHERE tenant_id = :tenantId AND subject_kind = :subjectKind
              AND subject_id = :subjectId AND forgotten_at IS NULL
              AND embedding IS NOT NULL
              AND (expires_at IS NULL OR expires_at > :at)
            ORDER BY embedding <=> cast(:queryVector AS vector), importance DESC
            LIMIT :maxItems
            """, nativeQuery = true)
    List<CognitionMemoryEntity> search(
            String tenantId,
            String subjectKind,
            String subjectId,
            String queryVector,
            int maxItems,
            Instant at);

    @Modifying
    @Query("""
            UPDATE CognitionMemoryEntity m SET m.forgottenAt = :at, m.updatedAt = :at
            WHERE m.tenantId = :tenantId AND m.subjectKind = :subjectKind
              AND m.subjectId = :subjectId AND m.memoryId IN :ids AND m.forgottenAt IS NULL
            """)
    int forget(String tenantId, String subjectKind, String subjectId, List<String> ids, Instant at);

    @Modifying
    @Query("""
            UPDATE CognitionMemoryEntity m SET m.subjectKind = 'USER', m.subjectId = :userId,
              m.expiresAt = NULL, m.updatedAt = :at
            WHERE m.tenantId = :tenantId AND m.subjectKind = 'VISITOR'
              AND m.subjectId = :visitorId AND m.forgottenAt IS NULL
              AND (m.expiresAt IS NULL OR m.expiresAt > :at)
            """)
    int mergeVisitor(String tenantId, String visitorId, String userId, Instant at);

    @Modifying
    @Query("""
            UPDATE CognitionMemoryEntity m SET m.forgottenAt = :at, m.updatedAt = :at
            WHERE m.subjectKind = 'VISITOR' AND m.forgottenAt IS NULL AND m.expiresAt <= :at
            """)
    int expireVisitors(Instant at);
}
