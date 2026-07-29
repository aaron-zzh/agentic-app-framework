package com.xuejiai.aaf.framework.intelligent.infrastructure.automation.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationDefinitionRepository
        extends JpaRepository<AutomationDefinitionEntity, Long> {
    Optional<AutomationDefinitionEntity>
            findFirstByTenantIdAndAutomationIdOrderByDefinitionVersionDesc(
                    String tenantId, String automationId);

    Optional<AutomationDefinitionEntity> findByTenantIdAndAutomationIdAndDefinitionVersion(
            String tenantId, String automationId, Long version);

    List<AutomationDefinitionEntity> findByTenantIdOrderByUpdatedAtDesc(String tenantId);
}
