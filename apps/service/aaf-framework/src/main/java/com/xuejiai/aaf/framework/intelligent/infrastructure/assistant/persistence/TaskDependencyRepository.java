package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskDependencyRepository extends JpaRepository<TaskDependencyEntity, Long> {
    List<TaskDependencyEntity>
            findByOrgIdAndTaskIdAndPlanIdAndPlanRevisionOrderBySuccessorNodeIdAscPredecessorNodeIdAsc(
                    Long orgId, String taskId, String planId, Integer planRevision);

    void deleteByOrgIdAndTaskIdAndPlanIdAndPlanRevision(
            Long orgId, String taskId, String planId, Integer planRevision);

    default List<TaskDependencyEntity>
            findByTenantIdAndTaskIdAndPlanIdAndPlanRevisionOrderBySuccessorNodeIdAscPredecessorNodeIdAsc(
                    String tenantId, String taskId, String planId, Integer planRevision) {
        return findByOrgIdAndTaskIdAndPlanIdAndPlanRevisionOrderBySuccessorNodeIdAscPredecessorNodeIdAsc(
                Long.valueOf(tenantId), taskId, planId, planRevision);
    }

    default void deleteByTenantIdAndTaskIdAndPlanIdAndPlanRevision(
            String tenantId, String taskId, String planId, Integer planRevision) {
        deleteByOrgIdAndTaskIdAndPlanIdAndPlanRevision(
                Long.valueOf(tenantId), taskId, planId, planRevision);
    }
}
