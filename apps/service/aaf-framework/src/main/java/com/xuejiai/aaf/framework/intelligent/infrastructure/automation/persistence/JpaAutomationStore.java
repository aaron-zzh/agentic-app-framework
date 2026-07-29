package com.xuejiai.aaf.framework.intelligent.infrastructure.automation.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationDefinition;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationGovernance.DefinitionLifecycle;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationGovernance.OrganizationPolicy;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationRun;
import com.xuejiai.aaf.framework.intelligent.automation.port.AutomationPorts.AuditPort;
import com.xuejiai.aaf.framework.intelligent.automation.port.AutomationPorts.AuditRecord;
import com.xuejiai.aaf.framework.intelligent.automation.port.AutomationPorts.DefinitionPort;
import com.xuejiai.aaf.framework.intelligent.automation.port.AutomationPorts.LifecyclePort;
import com.xuejiai.aaf.framework.intelligent.automation.port.AutomationPorts.PolicyPort;
import com.xuejiai.aaf.framework.intelligent.automation.port.AutomationPorts.RunPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** P5 自动化 PostgreSQL 唯一生产适配器。 */
public class JpaAutomationStore
        implements DefinitionPort, RunPort, PolicyPort, LifecyclePort, AuditPort {
    private final AutomationDefinitionRepository definitions;
    private final AutomationRunRepository runs;
    private final AutomationPolicyRepository policies;
    private final DefinitionLifecycleRepository lifecycles;
    private final AutomationAuditRepository audits;

    public JpaAutomationStore(
            AutomationDefinitionRepository definitions,
            AutomationRunRepository runs,
            AutomationPolicyRepository policies,
            DefinitionLifecycleRepository lifecycles,
            AutomationAuditRepository audits) {
        this.definitions = definitions;
        this.runs = runs;
        this.policies = policies;
        this.lifecycles = lifecycles;
        this.audits = audits;
    }

    @Override
    @Transactional
    public AutomationDefinition save(AutomationDefinition value) {
        var entity =
                definitions
                        .findByTenantIdAndAutomationIdAndDefinitionVersion(
                                value.tenantId().value(), value.automationId(), value.version())
                        .orElseGet(AutomationDefinitionEntity::new);
        entity.setTenantId(value.tenantId().value());
        entity.setAutomationId(value.automationId());
        entity.setDefinitionVersion(value.version());
        entity.setLifecycle(value.lifecycle().name());
        entity.setEnabled(value.enabled());
        entity.setDefinition(value);
        entity.setCreatedAt(value.createdAt());
        entity.setUpdatedAt(value.updatedAt());
        return definitions.saveAndFlush(entity).getDefinition();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AutomationDefinition> findLatest(TenantId tenantId, String automationId) {
        return definitions
                .findFirstByTenantIdAndAutomationIdOrderByDefinitionVersionDesc(
                        tenantId.value(), automationId)
                .map(AutomationDefinitionEntity::getDefinition);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AutomationDefinition> findVersion(
            TenantId tenantId, String automationId, long version) {
        return definitions
                .findByTenantIdAndAutomationIdAndDefinitionVersion(
                        tenantId.value(), automationId, version)
                .map(AutomationDefinitionEntity::getDefinition);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AutomationDefinition> list(TenantId tenantId) {
        return definitions.findByTenantIdOrderByUpdatedAtDesc(tenantId.value()).stream()
                .map(AutomationDefinitionEntity::getDefinition)
                .toList();
    }

    @Override
    public AutomationRun createOnce(AutomationRun run) {
        var existing = findByTrigger(run.tenantId(), run.automationId(), run.triggerKey());
        if (existing.isPresent()) return existing.get();
        var entity = toEntity(run);
        try {
            return runs.saveAndFlush(entity).getRun();
        } catch (DataIntegrityViolationException conflict) {
            return findByTrigger(run.tenantId(), run.automationId(), run.triggerKey())
                    .orElseThrow(() -> conflict);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AutomationRun> findByTrigger(
            TenantId tenantId, String automationId, String triggerKey) {
        return runs.findByTenantIdAndAutomationIdAndTriggerKey(
                        tenantId.value(), automationId, triggerKey)
                .map(AutomationRunEntity::getRun);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AutomationRun> history(TenantId tenantId, String automationId) {
        return runs
                .findByTenantIdAndAutomationIdOrderByCreatedAtDesc(tenantId.value(), automationId)
                .stream()
                .map(AutomationRunEntity::getRun)
                .toList();
    }

    @Override
    @Transactional
    public List<AutomationRun> findPending(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit 必须在 1..100");
        return runs
                .findByStatusOrderByCreatedAtAsc(
                        AutomationRun.Status.PENDING.name(), PageRequest.of(0, limit))
                .stream()
                .map(AutomationRunEntity::getRun)
                .toList();
    }

    @Override
    @Transactional
    public AutomationRun markDispatched(TenantId tenantId, String runId, Instant at) {
        return updateRun(tenantId, runId, AutomationRun.Status.DISPATCHED, null, at);
    }

    @Override
    @Transactional
    public AutomationRun markFailed(TenantId tenantId, String runId, String failure, Instant at) {
        return updateRun(tenantId, runId, AutomationRun.Status.FAILED, failure, at);
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationPolicy get(TenantId tenantId) {
        return policies.findById(tenantId.value())
                .map(AutomationPolicyEntity::getPolicy)
                .orElseGet(
                        () ->
                                new OrganizationPolicy(
                                        tenantId,
                                        false,
                                        Set.of(
                                                "permission.change",
                                                "security.change",
                                                "database.migrate",
                                                "data.delete.irreversible"),
                                        3,
                                        "default-v1",
                                        Instant.EPOCH));
    }

    @Override
    @Transactional
    public OrganizationPolicy save(OrganizationPolicy policy) {
        var entity =
                policies.findById(policy.tenantId().value()).orElseGet(AutomationPolicyEntity::new);
        entity.setTenantId(policy.tenantId().value());
        entity.setGlobalStop(policy.globalStop());
        entity.setPolicy(policy);
        entity.setUpdatedAt(policy.updatedAt());
        return policies.saveAndFlush(entity).getPolicy();
    }

    @Override
    @Transactional
    public DefinitionLifecycle save(DefinitionLifecycle value) {
        var entity =
                lifecycles
                        .findByTenantIdAndDefinitionKindAndDefinitionIdAndDefinitionVersion(
                                value.tenantId().value(),
                                value.kind().name(),
                                value.definitionId(),
                                value.version())
                        .orElseGet(DefinitionLifecycleEntity::new);
        entity.setTenantId(value.tenantId().value());
        entity.setDefinitionKind(value.kind().name());
        entity.setDefinitionId(value.definitionId());
        entity.setDefinitionVersion(value.version());
        entity.setState(value.state().name());
        entity.setLifecycle(value);
        entity.setUpdatedAt(value.updatedAt());
        return lifecycles.saveAndFlush(entity).getLifecycle();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DefinitionLifecycle> find(TenantId tenantId, DefinitionLifecycleKey key) {
        return lifecycles
                .findByTenantIdAndDefinitionKindAndDefinitionIdAndDefinitionVersion(
                        tenantId.value(), key.kind().name(), key.definitionId(), key.version())
                .map(DefinitionLifecycleEntity::getLifecycle);
    }

    @Override
    @Transactional
    public void append(AuditRecord value) {
        var entity = new AutomationAuditEntity();
        entity.setAuditId(value.auditId());
        entity.setTenantId(value.tenantId().value());
        entity.setAutomationId(value.automationId());
        entity.setAction(value.action());
        entity.setActorId(value.actorId());
        entity.setDetails(value.details());
        entity.setOccurredAt(value.occurredAt());
        audits.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditRecord> search(
            TenantId tenantId, String automationId, Instant from, Instant to) {
        return audits
                .findByTenantIdAndAutomationIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                        tenantId.value(), automationId, from, to)
                .stream()
                .map(
                        entity ->
                                new AuditRecord(
                                        tenantId,
                                        entity.getAuditId(),
                                        entity.getAutomationId(),
                                        entity.getAction(),
                                        entity.getActorId(),
                                        entity.getDetails(),
                                        entity.getOccurredAt()))
                .toList();
    }

    private AutomationRun updateRun(
            TenantId tenantId,
            String runId,
            AutomationRun.Status status,
            String failure,
            Instant at) {
        var entity =
                runs.findByTenantIdAndRunId(tenantId.value(), runId)
                        .orElseThrow(() -> new IllegalArgumentException("自动化运行不存在"));
        var current = entity.getRun();
        var changed =
                new AutomationRun(
                        current.tenantId(),
                        current.runId(),
                        current.automationId(),
                        current.definitionVersion(),
                        current.triggerKey(),
                        current.delegatedTaskId(),
                        current.definitionSnapshot(),
                        current.parameters(),
                        status,
                        failure,
                        current.createdAt(),
                        at);
        entity.setStatus(status.name());
        entity.setRun(changed);
        entity.setUpdatedAt(at);
        return runs.saveAndFlush(entity).getRun();
    }

    private static AutomationRunEntity toEntity(AutomationRun run) {
        var entity = new AutomationRunEntity();
        entity.setTenantId(run.tenantId().value());
        entity.setRunId(run.runId());
        entity.setAutomationId(run.automationId());
        entity.setDefinitionVersion(run.definitionVersion());
        entity.setTriggerKey(run.triggerKey());
        entity.setDelegatedTaskId(run.delegatedTaskId().value());
        entity.setStatus(run.status().name());
        entity.setRun(run);
        entity.setCreatedAt(run.createdAt());
        entity.setUpdatedAt(run.updatedAt());
        return entity;
    }
}
