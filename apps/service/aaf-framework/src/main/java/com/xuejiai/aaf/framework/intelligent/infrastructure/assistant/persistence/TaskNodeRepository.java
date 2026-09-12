package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface TaskNodeRepository extends JpaRepository<TaskNodeEntity, Long> {
    List<TaskNodeEntity> findByOrgIdAndTaskIdAndPlanIdAndPlanRevisionOrderByNodeId(
            Long orgId, String taskId, String planId, Integer planRevision);

    default List<TaskNodeEntity> findByTenantIdAndTaskIdAndPlanIdAndPlanRevisionOrderByNodeId(
            String tenantId, String taskId, String planId, Integer planRevision) {
        return findByOrgIdAndTaskIdAndPlanIdAndPlanRevisionOrderByNodeId(
                Long.valueOf(tenantId), taskId, planId, planRevision);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select n from TaskNodeEntity n
            where n.orgId = :orgId and n.taskId = :taskId
              and n.planId = :planId and n.planRevision = :revision
            order by n.nodeId
            """)
    List<TaskNodeEntity> findPlanNodesForUpdateByOrgId(
            Long orgId, String taskId, String planId, Integer revision);

    default List<TaskNodeEntity> findPlanNodesForUpdate(
            String tenantId, String taskId, String planId, Integer revision) {
        return findPlanNodesForUpdateByOrgId(Long.valueOf(tenantId), taskId, planId, revision);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select n from TaskNodeEntity n
            where n.orgId = :orgId and n.taskId = :taskId
              and n.planId = :planId and n.planRevision = :revision
              and n.nodeId = :nodeId
            """)
    Optional<TaskNodeEntity> findNodeForUpdateByOrgId(
            Long orgId, String taskId, String planId, Integer revision, String nodeId);

    default Optional<TaskNodeEntity> findNodeForUpdate(
            String tenantId, String taskId, String planId, Integer revision, String nodeId) {
        return findNodeForUpdateByOrgId(Long.valueOf(tenantId), taskId, planId, revision, nodeId);
    }

    @Query(
            """
            select count(n) from TaskNodeEntity n
            where n.orgId = :orgId and n.taskId = :taskId
              and n.planId = :planId and n.planRevision = :revision
              and n.status in ('CLAIMED', 'RUNNING')
            """)
    long countClaimedOrRunningByOrgId(Long orgId, String taskId, String planId, Integer revision);

    default long countClaimedOrRunning(
            String tenantId, String taskId, String planId, Integer revision) {
        return countClaimedOrRunningByOrgId(Long.valueOf(tenantId), taskId, planId, revision);
    }
}
