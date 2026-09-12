package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface TaskInputRepository extends JpaRepository<TaskInputEntity, Long> {
    Optional<TaskInputEntity> findByOrgIdAndInputId(Long orgId, String inputId);

    default Optional<TaskInputEntity> findByTenantIdAndInputId(String tenantId, String inputId) {
        return findByOrgIdAndInputId(Long.valueOf(tenantId), inputId);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from TaskInputEntity i where i.orgId = :orgId and i.inputId = :inputId")
    Optional<TaskInputEntity> findForUpdateByOrgId(Long orgId, String inputId);

    default Optional<TaskInputEntity> findForUpdate(String tenantId, String inputId) {
        return findForUpdateByOrgId(Long.valueOf(tenantId), inputId);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select i from TaskInputEntity i
            where i.orgId = :orgId and i.taskId = :taskId and i.consumedAt is null
            order by i.receivedAt asc, i.inputId asc
            """)
    List<TaskInputEntity> findPendingForUpdateByOrgId(Long orgId, String taskId);

    default List<TaskInputEntity> findPendingForUpdate(String tenantId, String taskId) {
        return findPendingForUpdateByOrgId(Long.valueOf(tenantId), taskId);
    }
}
