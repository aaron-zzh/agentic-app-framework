package com.xuejiai.aaf.framework.intelligent.infrastructure.automation.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DefinitionLifecycleRepository
        extends JpaRepository<DefinitionLifecycleEntity, Long> {
    Optional<DefinitionLifecycleEntity>
            findByTenantIdAndDefinitionKindAndDefinitionIdAndDefinitionVersion(
                    String tenantId, String kind, String definitionId, Long version);
}
