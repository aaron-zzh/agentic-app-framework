package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface DelegatedTaskRepository extends JpaRepository<DelegatedTaskEntity, Long> {

    Optional<DelegatedTaskEntity> findByTenantIdAndTaskId(String tenantId, String taskId);

    List<DelegatedTaskEntity> findByTenantIdAndUserIdOrderByUpdatedAtDesc(
            String tenantId, String userId);

    List<DelegatedTaskEntity>
            findByTenantIdAndConversationIdAndStatusOrderByPriorityAscCreatedAtAsc(
                    String tenantId, String conversationId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select t from DelegatedTaskEntity t where t.tenantId = :tenantId and t.taskId = :taskId")
    Optional<DelegatedTaskEntity> findForUpdate(String tenantId, String taskId);

    @Query(
            """
            select t from DelegatedTaskEntity t
            where t.status = 'PENDING' and t.nextRunAt <= :now and t.ownerKind <> 'HUMAN'
            order by t.nextRunAt asc, t.id asc
            """)
    List<DelegatedTaskEntity> findDispatchable(Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select t from DelegatedTaskEntity t
            where t.status = 'RUNNING' and t.leaseUntil < :now
            order by t.leaseUntil asc
            """)
    List<DelegatedTaskEntity> findExpiredRunning(Instant now);
}
