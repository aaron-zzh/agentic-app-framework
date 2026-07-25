package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EffectiveContextManifestRepository
        extends JpaRepository<EffectiveContextManifestEntity, Long> {

    Optional<EffectiveContextManifestEntity> findByTenantIdAndTaskId(
            String tenantId, String taskId);
}
