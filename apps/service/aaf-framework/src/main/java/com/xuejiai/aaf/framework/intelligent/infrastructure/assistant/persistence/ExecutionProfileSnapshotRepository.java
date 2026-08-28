package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionProfileSnapshotRepository
        extends JpaRepository<ExecutionProfileSnapshotEntity, Long> {

    Optional<ExecutionProfileSnapshotEntity> findFirstByTenantIdAndExecutionIdOrderByFrozenAtDesc(
            String tenantId, String executionId);
}
