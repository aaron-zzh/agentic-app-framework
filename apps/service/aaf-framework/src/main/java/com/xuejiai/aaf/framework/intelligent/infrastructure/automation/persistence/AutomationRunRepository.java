package com.xuejiai.aaf.framework.intelligent.infrastructure.automation.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface AutomationRunRepository extends JpaRepository<AutomationRunEntity, Long> {
    Optional<AutomationRunEntity> findByTenantIdAndAutomationIdAndTriggerKey(
            String tenantId, String automationId, String triggerKey);

    Optional<AutomationRunEntity> findByTenantIdAndRunId(String tenantId, String runId);

    List<AutomationRunEntity> findByTenantIdAndAutomationIdOrderByCreatedAtDesc(
            String tenantId, String automationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AutomationRunEntity> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);
}
