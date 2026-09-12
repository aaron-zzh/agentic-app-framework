package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface TaskRootRepository extends JpaRepository<TaskRootEntity, Long> {
    Optional<TaskRootEntity> findByOrgIdAndTaskId(Long orgId, String taskId);

    Optional<TaskRootEntity> findByOrgIdAndOriginExecutionId(Long orgId, String originExecutionId);

    List<TaskRootEntity> findByOrgIdAndUserIdOrderByIdDesc(Long orgId, String userId);

    List<TaskRootEntity> findByOrgIdAndConversationIdAndUserIdOrderByIdDesc(
            Long orgId, String conversationId, String userId);

    List<TaskRootEntity> findByOrgIdAndConversationIdAndStatusOrderByIdAsc(
            Long orgId, String conversationId, String status);

    List<TaskRootEntity> findByStatusOrderByIdAsc(String status, Pageable pageable);

    default List<TaskRootEntity> findByTenantIdAndConversationIdAndUserIdOrderByIdDesc(
            String tenantId, String conversationId, String userId) {
        return findByOrgIdAndConversationIdAndUserIdOrderByIdDesc(
                Long.valueOf(tenantId), conversationId, userId);
    }

    default Optional<TaskRootEntity> findByTenantIdAndTaskId(String tenantId, String taskId) {
        return findByOrgIdAndTaskId(Long.valueOf(tenantId), taskId);
    }

    default Optional<TaskRootEntity> findByTenantIdAndOriginExecutionId(
            String tenantId, String originExecutionId) {
        return findByOrgIdAndOriginExecutionId(Long.valueOf(tenantId), originExecutionId);
    }

    default List<TaskRootEntity> findByTenantIdAndUserIdOrderByIdDesc(
            String tenantId, String userId) {
        return findByOrgIdAndUserIdOrderByIdDesc(Long.valueOf(tenantId), userId);
    }

    default List<TaskRootEntity> findByTenantIdAndConversationIdAndStatusOrderByIdAsc(
            String tenantId, String conversationId, String status) {
        return findByOrgIdAndConversationIdAndStatusOrderByIdAsc(
                Long.valueOf(tenantId), conversationId, status);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TaskRootEntity t where t.orgId = :orgId and t.taskId = :taskId")
    Optional<TaskRootEntity> findForUpdateByOrgId(Long orgId, String taskId);

    default Optional<TaskRootEntity> findForUpdate(String tenantId, String taskId) {
        return findForUpdateByOrgId(Long.valueOf(tenantId), taskId);
    }
}
