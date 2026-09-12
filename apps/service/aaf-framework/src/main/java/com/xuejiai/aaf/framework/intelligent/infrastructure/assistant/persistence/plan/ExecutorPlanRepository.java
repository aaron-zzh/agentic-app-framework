package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.plan;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface ExecutorPlanRepository extends JpaRepository<ExecutorPlanEntity, Long> {

    Optional<ExecutorPlanEntity> findByPlanId(String planId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ExecutorPlanEntity p where p.planId = :planId")
    Optional<ExecutorPlanEntity> findForUpdate(String planId);

    @Query(
            """
            select p from ExecutorPlanEntity p
            where p.orgId = :orgId and p.taskId = :taskId and p.nodeId = :nodeId
                and p.status not in ('COMPLETED', 'FAILED', 'CANCELLED', 'REJECTED')
            order by p.revision desc
            """)
    List<ExecutorPlanEntity> findActiveCandidatesByOrgId(Long orgId, String taskId, String nodeId);

    @Query(
            """
            select p from ExecutorPlanEntity p
            where p.orgId = :orgId and p.taskId = :taskId
            order by p.nodeId asc, p.revision desc
            """)
    List<ExecutorPlanEntity> findByOrgIdAndTaskIdOrderByNodeAndRevision(Long orgId, String taskId);

    @Query(
            """
            select max(p.revision) from ExecutorPlanEntity p
            where p.orgId = :orgId and p.taskId = :taskId and p.nodeId = :nodeId
            """)
    Optional<Integer> findMaxRevisionByOrgId(Long orgId, String taskId, String nodeId);

    default List<ExecutorPlanEntity> findActiveCandidates(
            String tenantId, String taskId, String nodeId) {
        return findActiveCandidatesByOrgId(Long.valueOf(tenantId), taskId, nodeId);
    }

    default List<ExecutorPlanEntity> findByTenantIdAndTaskIdOrderByNodeAndRevision(
            String tenantId, String taskId) {
        return findByOrgIdAndTaskIdOrderByNodeAndRevision(Long.valueOf(tenantId), taskId);
    }

    default Optional<Integer> findMaxRevision(String tenantId, String taskId, String nodeId) {
        return findMaxRevisionByOrgId(Long.valueOf(tenantId), taskId, nodeId);
    }
}
