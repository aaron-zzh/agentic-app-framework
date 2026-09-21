package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface AssistantTaskExecutionRepository extends JpaRepository<TaskExecutionEntity, Long> {
    List<TaskExecutionEntity> findByStatusOrderByUpdatedAtAscIdAsc(
            String status, Pageable pageable);

    @Query(
            value =
                    """
                    select * from ai_task_execution
                    where deleted = false
                      and status = 'AWAITING_EXTERNAL'
                      and artifact_completion_receipt -> 'executionRunIds'
                          @> jsonb_build_array(:executionRunId)
                    order by id asc
                    """,
            nativeQuery = true)
    List<TaskExecutionEntity> findAwaitingExternalByRunId(Long executionRunId);

    List<TaskExecutionEntity> findByOrgIdAndTaskIdAndStatusOrderByIdAsc(
            Long orgId, String taskId, String status);

    Optional<TaskExecutionEntity> findByOrgIdAndExecutionId(Long orgId, String executionId);

    Optional<TaskExecutionEntity> findFirstByOrgIdAndTaskIdAndNodeIdOrderByAttemptNoDesc(
            Long orgId, String taskId, String nodeId);

    List<TaskExecutionEntity> findByOrgIdAndTaskIdOrderByAttemptNoAsc(Long orgId, String taskId);

    default Optional<TaskExecutionEntity> findByTenantIdAndExecutionId(
            String tenantId, String executionId) {
        return findByOrgIdAndExecutionId(Long.valueOf(tenantId), executionId);
    }

    default Optional<TaskExecutionEntity> findFirstByTenantIdAndTaskIdAndNodeIdOrderByAttemptNoDesc(
            String tenantId, String taskId, String nodeId) {
        return findFirstByOrgIdAndTaskIdAndNodeIdOrderByAttemptNoDesc(
                Long.valueOf(tenantId), taskId, nodeId);
    }

    default List<TaskExecutionEntity> findByTenantIdAndTaskIdOrderByAttemptNoAsc(
            String tenantId, String taskId) {
        return findByOrgIdAndTaskIdOrderByAttemptNoAsc(Long.valueOf(tenantId), taskId);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select e from TaskExecutionEntity e where e.orgId = :orgId and e.executionId = :executionId")
    Optional<TaskExecutionEntity> findForUpdateByOrgId(Long orgId, String executionId);

    default Optional<TaskExecutionEntity> findForUpdate(String tenantId, String executionId) {
        return findForUpdateByOrgId(Long.valueOf(tenantId), executionId);
    }
}
