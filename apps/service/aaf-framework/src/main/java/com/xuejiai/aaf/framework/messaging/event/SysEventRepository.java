package com.xuejiai.aaf.framework.messaging.event;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysEventRepository extends JpaRepository<SysEvent, Long> {

    /** 用户级发布串行化：同一用户的发布在事务内互斥，保证每条连接看到的游标单调、不跳号。 */
    @Query(
            value =
                    """
                    SELECT 1
                    FROM pg_advisory_xact_lock(
                        hashtextextended(concat('sys-event:', cast(:ownerId as text)), 0))
                    """,
            nativeQuery = true)
    int lockOwner(@Param("ownerId") Long ownerId);

    /** 订阅方可见事件的最大 id：用户级事件投递所有范围，范围事件仅精确匹配。 */
    @Query(
            """
            SELECT COALESCE(MAX(event.id), 0)
            FROM SysEvent event
            WHERE event.ownerId = :ownerId
              AND ((event.orgId IS NULL AND event.workspaceId IS NULL)
                   OR (((:orgId IS NULL AND event.orgId IS NULL) OR event.orgId = :orgId)
                       AND ((:workspaceId IS NULL AND event.workspaceId IS NULL)
                            OR event.workspaceId = :workspaceId)))
              AND event.deleted = false
            """)
    long replayUpperBound(
            @Param("ownerId") Long ownerId,
            @Param("orgId") Long orgId,
            @Param("workspaceId") Long workspaceId);

    @Query(
            """
            SELECT event
            FROM SysEvent event
            WHERE event.ownerId = :ownerId
              AND ((event.orgId IS NULL AND event.workspaceId IS NULL)
                   OR (((:orgId IS NULL AND event.orgId IS NULL) OR event.orgId = :orgId)
                       AND ((:workspaceId IS NULL AND event.workspaceId IS NULL)
                            OR event.workspaceId = :workspaceId)))
              AND event.id > :afterId
              AND event.id <= :upperBound
              AND event.deleted = false
            ORDER BY event.id ASC
            """)
    List<SysEvent> replay(
            @Param("ownerId") Long ownerId,
            @Param("orgId") Long orgId,
            @Param("workspaceId") Long workspaceId,
            @Param("afterId") Long afterId,
            @Param("upperBound") Long upperBound,
            Pageable pageable);
}
