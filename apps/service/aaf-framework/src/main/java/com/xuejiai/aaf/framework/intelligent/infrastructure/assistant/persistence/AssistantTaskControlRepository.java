package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Assistant 任务控制仓储。 */
public interface AssistantTaskControlRepository
        extends JpaRepository<AssistantTaskControlEntity, Long> {

    Optional<AssistantTaskControlEntity> findByTenantIdAndTaskId(String tenantId, String taskId);
}
