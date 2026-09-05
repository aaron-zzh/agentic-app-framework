package com.xuejiai.aaf.module.ai.aigc.event.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.ai.aigc.event.domain.AigcActivityEvent;

public interface AigcActivityEventRepository extends JpaRepository<AigcActivityEvent, Long> {

    @Query(
            value =
                    """
                    SELECT 1
                    FROM pg_advisory_xact_lock(
                        hashtextextended(
                            concat(
                                cast(:ownerId as text), ':',
                                coalesce(cast(:orgId as text), 'null'), ':',
                                coalesce(cast(:workspaceId as text), 'null')),
                            0))
                    """,
            nativeQuery = true)
    int lockScope(
            @Param("ownerId") Long ownerId,
            @Param("orgId") Long orgId,
            @Param("workspaceId") Long workspaceId);

    @Query(
            """
            SELECT COALESCE(MAX(event.id), 0)
            FROM AigcActivityEvent event
            WHERE event.ownerId = :ownerId
              AND ((:orgId IS NULL AND event.orgId IS NULL) OR event.orgId = :orgId)
              AND ((:workspaceId IS NULL AND event.workspaceId IS NULL)
                   OR event.workspaceId = :workspaceId)
              AND event.deleted = false
            """)
    long replayUpperBound(
            @Param("ownerId") Long ownerId,
            @Param("orgId") Long orgId,
            @Param("workspaceId") Long workspaceId);

    @Query(
            """
            SELECT event
            FROM AigcActivityEvent event
            WHERE event.ownerId = :ownerId
              AND ((:orgId IS NULL AND event.orgId IS NULL) OR event.orgId = :orgId)
              AND ((:workspaceId IS NULL AND event.workspaceId IS NULL)
                   OR event.workspaceId = :workspaceId)
              AND event.id > :afterId
              AND event.id <= :upperBound
              AND event.deleted = false
            ORDER BY event.id ASC
            """)
    List<AigcActivityEvent> replay(
            @Param("ownerId") Long ownerId,
            @Param("orgId") Long orgId,
            @Param("workspaceId") Long workspaceId,
            @Param("afterId") Long afterId,
            @Param("upperBound") Long upperBound,
            Pageable pageable);
}
