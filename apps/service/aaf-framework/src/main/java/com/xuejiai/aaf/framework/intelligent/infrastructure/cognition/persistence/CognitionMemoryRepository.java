package com.xuejiai.aaf.framework.intelligent.infrastructure.cognition.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CognitionMemoryRepository extends JpaRepository<CognitionMemoryEntity, String> {

    @Query(
            """
            SELECT m FROM CognitionMemoryEntity m
            WHERE m.tenantId = :tenantId AND m.subjectKind = :subjectKind
              AND m.subjectId = :subjectId AND m.forgottenAt IS NULL
              AND (m.expiresAt IS NULL OR m.expiresAt > :at)
            ORDER BY m.importance DESC, m.createdAt DESC
            """)
    List<CognitionMemoryEntity> findActive(
            String tenantId, String subjectKind, String subjectId, Instant at);

    @Query(
            value =
                    """
            SELECT * FROM ai_cognition_memory
            WHERE tenant_id = :tenantId AND subject_kind = :subjectKind
              AND subject_id = :subjectId AND forgotten_at IS NULL
              AND embedding IS NOT NULL
              AND (expires_at IS NULL OR expires_at > :at)
            ORDER BY embedding <=> cast(:queryVector AS vector), importance DESC
            LIMIT :maxItems
            """,
            nativeQuery = true)
    List<CognitionMemoryEntity> search(
            String tenantId,
            String subjectKind,
            String subjectId,
            String queryVector,
            int maxItems,
            Instant at);

    @Query(
            value =
                    """
            SELECT * FROM ai_cognition_memory
            WHERE tenant_id = :tenantId AND subject_kind = :subjectKind
              AND subject_id = :subjectId AND forgotten_at IS NULL
              AND (expires_at IS NULL OR expires_at > :at)
              AND (
                :scopeTag IS NULL
                OR :scopeTag = ANY(COALESCE(tags, ARRAY[]::text[]))
                OR (:scopeTag = 'scope:long_term' AND NOT EXISTS (
                    SELECT 1 FROM unnest(COALESCE(tags, ARRAY[]::text[])) memory_tag
                    WHERE memory_tag LIKE 'scope:%'))
              )
            ORDER BY importance DESC, created_at DESC
            LIMIT :limit OFFSET :offset
            """,
            nativeQuery = true)
    List<CognitionMemoryEntity> listManaged(
            String tenantId,
            String subjectKind,
            String subjectId,
            String scopeTag,
            int limit,
            int offset,
            Instant at);

    @Query(
            value =
                    """
            SELECT * FROM ai_cognition_memory
            WHERE tenant_id = :tenantId AND subject_kind = :subjectKind
              AND subject_id = :subjectId AND forgotten_at IS NULL
              AND (expires_at IS NULL OR expires_at > :at)
              AND content ILIKE CONCAT('%', :keyword, '%')
              AND (
                :scopeTag IS NULL
                OR :scopeTag = ANY(COALESCE(tags, ARRAY[]::text[]))
                OR (:scopeTag = 'scope:long_term' AND NOT EXISTS (
                    SELECT 1 FROM unnest(COALESCE(tags, ARRAY[]::text[])) memory_tag
                    WHERE memory_tag LIKE 'scope:%'))
              )
            ORDER BY importance DESC, created_at DESC
            LIMIT :limit
            """,
            nativeQuery = true)
    List<CognitionMemoryEntity> searchManaged(
            String tenantId,
            String subjectKind,
            String subjectId,
            String keyword,
            String scopeTag,
            int limit,
            Instant at);

    @Query(
            value =
                    """
            SELECT COUNT(*) FROM ai_cognition_memory
            WHERE tenant_id = :tenantId AND subject_kind = :subjectKind
              AND subject_id = :subjectId AND forgotten_at IS NULL
              AND (expires_at IS NULL OR expires_at > :at)
              AND (
                :scopeTag IS NULL
                OR :scopeTag = ANY(COALESCE(tags, ARRAY[]::text[]))
                OR (:scopeTag = 'scope:long_term' AND NOT EXISTS (
                    SELECT 1 FROM unnest(COALESCE(tags, ARRAY[]::text[])) memory_tag
                    WHERE memory_tag LIKE 'scope:%'))
              )
            """,
            nativeQuery = true)
    long countManaged(
            String tenantId,
            String subjectKind,
            String subjectId,
            String scopeTag,
            Instant at);

    @Modifying
    @Query(
            value =
                    """
            UPDATE ai_cognition_memory
            SET forgotten_at = :at, updated_at = :at
            WHERE tenant_id = :tenantId AND subject_kind = :subjectKind
              AND subject_id = :subjectId AND forgotten_at IS NULL
              AND (expires_at IS NULL OR expires_at > :at)
              AND (
                :scopeTag = ANY(COALESCE(tags, ARRAY[]::text[]))
                OR (:scopeTag = 'scope:long_term' AND NOT EXISTS (
                    SELECT 1 FROM unnest(COALESCE(tags, ARRAY[]::text[])) memory_tag
                    WHERE memory_tag LIKE 'scope:%'))
              )
            """,
            nativeQuery = true)
    int forgetManagedScope(
            String tenantId,
            String subjectKind,
            String subjectId,
            String scopeTag,
            Instant at);

    @Modifying
    @Query(
            """
            UPDATE CognitionMemoryEntity m SET m.forgottenAt = :at, m.updatedAt = :at
            WHERE m.tenantId = :tenantId AND m.subjectKind = :subjectKind
              AND m.subjectId = :subjectId AND m.memoryId IN :ids AND m.forgottenAt IS NULL
            """)
    int forget(String tenantId, String subjectKind, String subjectId, List<String> ids, Instant at);

    @Modifying
    @Query(
            """
            UPDATE CognitionMemoryEntity m SET m.subjectKind = 'USER', m.subjectId = :userId,
              m.expiresAt = NULL, m.updatedAt = :at
            WHERE m.tenantId = :tenantId AND m.subjectKind = 'VISITOR'
              AND m.subjectId = :visitorId AND m.forgottenAt IS NULL
              AND (m.expiresAt IS NULL OR m.expiresAt > :at)
            """)
    int mergeVisitor(String tenantId, String visitorId, String userId, Instant at);

    @Modifying
    @Query(
            """
            UPDATE CognitionMemoryEntity m SET m.forgottenAt = :at, m.updatedAt = :at
            WHERE m.subjectKind = 'VISITOR' AND m.forgottenAt IS NULL AND m.expiresAt <= :at
            """)
    int expireVisitors(Instant at);
}
