package com.xuejiai.aaf.framework.intelligent.infrastructure.automation.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationAuditRepository extends JpaRepository<AutomationAuditEntity, String> {
    List<AutomationAuditEntity>
            findByTenantIdAndAutomationIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                    String tenantId, String automationId, Instant from, Instant to);
}
