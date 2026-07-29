package com.xuejiai.aaf.framework.intelligent.automation.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationDefinition;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationGovernance.DefinitionLifecycle;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationGovernance.OrganizationPolicy;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationRun;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** P5 自动化六边形端口集合。 */
public final class AutomationPorts {
    private AutomationPorts() {}

    public interface DefinitionPort {
        AutomationDefinition save(AutomationDefinition definition);

        Optional<AutomationDefinition> findLatest(TenantId tenantId, String automationId);

        Optional<AutomationDefinition> findVersion(
                TenantId tenantId, String automationId, long version);

        List<AutomationDefinition> list(TenantId tenantId);
    }

    public interface RunPort {
        AutomationRun createOnce(AutomationRun run);

        Optional<AutomationRun> findByTrigger(
                TenantId tenantId, String automationId, String triggerKey);

        List<AutomationRun> history(TenantId tenantId, String automationId);

        List<AutomationRun> findPending(int limit);

        AutomationRun markDispatched(TenantId tenantId, String runId, Instant at);

        AutomationRun markFailed(TenantId tenantId, String runId, String failure, Instant at);
    }

    public interface PolicyPort {
        OrganizationPolicy get(TenantId tenantId);

        OrganizationPolicy save(OrganizationPolicy policy);
    }

    public interface LifecyclePort {
        DefinitionLifecycle save(DefinitionLifecycle lifecycle);

        Optional<DefinitionLifecycle> find(TenantId tenantId, DefinitionLifecycleKey key);

        record DefinitionLifecycleKey(
                com.xuejiai.aaf.framework.intelligent.automation.model.AutomationGovernance
                                .DefinitionKind
                        kind,
                String definitionId,
                long version) {}
    }

    public interface AuditPort {
        void append(AuditRecord record);

        List<AuditRecord> search(TenantId tenantId, String automationId, Instant from, Instant to);
    }

    public interface DispatchPort {
        void dispatch(AutomationRun run);
    }

    public record AuditRecord(
            TenantId tenantId,
            String auditId,
            String automationId,
            String action,
            String actorId,
            Map<String, Object> details,
            Instant occurredAt) {
        public AuditRecord {
            details = details == null ? Map.of() : Map.copyOf(details);
        }
    }
}
