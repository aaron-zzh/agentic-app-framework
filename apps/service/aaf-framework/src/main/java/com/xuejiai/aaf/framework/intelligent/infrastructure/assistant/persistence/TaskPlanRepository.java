package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface TaskPlanRepository extends JpaRepository<TaskPlanEntity, Long> {
    Optional<TaskPlanEntity> findByOrgIdAndTaskIdAndPlanIdAndPlanRevision(
            Long orgId, String taskId, String planId, Integer planRevision);

    default Optional<TaskPlanEntity> findByTenantIdAndTaskIdAndPlanIdAndPlanRevision(
            String tenantId, String taskId, String planId, Integer planRevision) {
        return findByOrgIdAndTaskIdAndPlanIdAndPlanRevision(
                Long.valueOf(tenantId), taskId, planId, planRevision);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select p from TaskPlanEntity p
            where p.orgId = :orgId and p.taskId = :taskId
              and p.planId = :planId and p.planRevision = :revision
            """)
    Optional<TaskPlanEntity> findCurrentForUpdateByOrgId(
            Long orgId, String taskId, String planId, Integer revision);

    default Optional<TaskPlanEntity> findCurrentForUpdate(
            String tenantId, String taskId, String planId, Integer revision) {
        return findCurrentForUpdateByOrgId(Long.valueOf(tenantId), taskId, planId, revision);
    }
}
