package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface TaskDispatchRepository extends JpaRepository<TaskDispatchEntity, Long> {
    Optional<TaskDispatchEntity> findByOrgIdAndDispatchId(Long orgId, String dispatchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select d from TaskDispatchEntity d
            where d.orgId = :orgId and d.dispatchId = :dispatchId
            """)
    Optional<TaskDispatchEntity> findForUpdateByOrgIdAndDispatchId(Long orgId, String dispatchId);

    default Optional<TaskDispatchEntity> findForUpdateByTenantIdAndDispatchId(
            String tenantId, String dispatchId) {
        return findForUpdateByOrgIdAndDispatchId(Long.valueOf(tenantId), dispatchId);
    }

    Optional<TaskDispatchEntity> findFirstByOrgIdAndExecutionIdOrderByIdDesc(
            Long orgId, String executionId);

    default Optional<TaskDispatchEntity> findByTenantIdAndDispatchId(
            String tenantId, String dispatchId) {
        return findByOrgIdAndDispatchId(Long.valueOf(tenantId), dispatchId);
    }

    default Optional<TaskDispatchEntity> findFirstByTenantIdAndExecutionIdOrderByIdDesc(
            String tenantId, String executionId) {
        return findFirstByOrgIdAndExecutionIdOrderByIdDesc(Long.valueOf(tenantId), executionId);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select d from TaskDispatchEntity d
            where d.orgId = :orgId and d.executionId = :executionId
              and d.status in ('PENDING', 'CLAIMED')
            """)
    Optional<TaskDispatchEntity> findActiveForUpdateByOrgId(Long orgId, String executionId);

    default Optional<TaskDispatchEntity> findActiveForUpdate(String tenantId, String executionId) {
        return findActiveForUpdateByOrgId(Long.valueOf(tenantId), executionId);
    }

    @Query(
            value =
                    """
                    select * from ai_task_dispatch
                    where org_id = :orgId and dispatch_id = :dispatchId
                      and status = 'PENDING' and next_run_at <= :now
                    for update skip locked
                    """,
            nativeQuery = true)
    Optional<TaskDispatchEntity> claimPendingByOrgId(Long orgId, String dispatchId, Instant now);

    default Optional<TaskDispatchEntity> claimPending(
            String tenantId, String dispatchId, Instant now) {
        return claimPendingByOrgId(Long.valueOf(tenantId), dispatchId, now);
    }

    @Query(
            """
            select d from TaskDispatchEntity d
            where d.status = 'PENDING' and d.nextRunAt <= :now
              and exists (
                  select e.id from TaskExecutionEntity e
                  where e.orgId = d.orgId and e.executionId = d.executionId
                    and e.scope = :executionScope
              )
            order by d.nextRunAt asc, d.id asc
            """)
    List<TaskDispatchEntity> findDispatchableByExecutionScope(
            String executionScope, Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select d from TaskDispatchEntity d
            where d.status = 'CLAIMED' and d.leaseUntil < :now
              and exists (
                  select e.id from TaskExecutionEntity e
                  where e.orgId = d.orgId and e.executionId = d.executionId
                    and e.scope = :executionScope
              )
            order by d.leaseUntil asc
            """)
    List<TaskDispatchEntity> findExpiredLeasesByExecutionScope(String executionScope, Instant now);

    @Query(value = "select nextval('ai_task_fence_seq')", nativeQuery = true)
    long nextFence();
}
