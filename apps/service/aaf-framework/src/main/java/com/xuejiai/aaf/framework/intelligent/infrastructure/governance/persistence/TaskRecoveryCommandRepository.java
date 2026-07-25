package com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRecoveryCommandRepository
        extends JpaRepository<TaskRecoveryCommandEntity, String> {

    Optional<TaskRecoveryCommandEntity> findByTenantIdAndTaskId(String tenantId, String taskId);
}
