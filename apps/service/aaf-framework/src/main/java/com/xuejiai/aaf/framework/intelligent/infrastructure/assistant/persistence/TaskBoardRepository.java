package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface TaskBoardRepository extends JpaRepository<TaskBoardEntity, Long> {
    Optional<TaskBoardEntity> findByTenantIdAndTaskId(String tenantId, String taskId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TaskBoardEntity> findLockedByTenantIdAndTaskId(String tenantId, String taskId);
}
