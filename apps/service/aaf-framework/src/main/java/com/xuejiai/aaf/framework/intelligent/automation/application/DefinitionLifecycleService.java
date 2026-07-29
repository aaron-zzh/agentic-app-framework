package com.xuejiai.aaf.framework.intelligent.automation.application;

import java.time.Clock;

import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationGovernance.*;
import com.xuejiai.aaf.framework.intelligent.automation.port.AutomationPorts.LifecyclePort;
import com.xuejiai.aaf.framework.intelligent.automation.port.AutomationPorts.LifecyclePort.DefinitionLifecycleKey;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** 非 Team 定义的统一生命周期治理。 */
public final class DefinitionLifecycleService {
    private final LifecyclePort lifecycles;
    private final Clock clock;

    public DefinitionLifecycleService(LifecyclePort lifecycles, Clock clock) {
        this.lifecycles = lifecycles;
        this.clock = clock;
    }

    public DefinitionLifecycle draft(
            TenantId tenantId,
            DefinitionKind kind,
            String id,
            long version,
            CompatibilityImpact impact) {
        return lifecycles.save(
                new DefinitionLifecycle(
                        tenantId,
                        kind,
                        id,
                        version,
                        State.DRAFT,
                        new Review(ReviewStatus.PENDING, null, "等待审核", null),
                        impact,
                        null,
                        clock.instant()));
    }

    public DefinitionLifecycle review(
            TenantId tenantId,
            DefinitionKind kind,
            String id,
            long version,
            ReviewStatus status,
            String reviewer,
            String reason) {
        var current = require(tenantId, kind, id, version);
        return lifecycles.save(
                new DefinitionLifecycle(
                        tenantId,
                        kind,
                        id,
                        version,
                        current.state(),
                        new Review(status, reviewer, reason, clock.instant()),
                        current.compatibilityImpact(),
                        current.rollbackVersion(),
                        clock.instant()));
    }

    public DefinitionLifecycle publish(
            TenantId tenantId, DefinitionKind kind, String id, long version) {
        var current = require(tenantId, kind, id, version);
        current.review().requireApproved();
        return transition(current, State.PUBLISHED, null);
    }

    public DefinitionLifecycle deprecate(
            TenantId tenantId, DefinitionKind kind, String id, long version) {
        return transition(require(tenantId, kind, id, version), State.DEPRECATED, null);
    }

    public DefinitionLifecycle disable(
            TenantId tenantId, DefinitionKind kind, String id, long version) {
        return transition(require(tenantId, kind, id, version), State.DISABLED, null);
    }

    public DefinitionLifecycle rollback(
            TenantId tenantId,
            DefinitionKind kind,
            String id,
            long currentVersion,
            long targetVersion) {
        var target = require(tenantId, kind, id, targetVersion);
        target.review().requireApproved();
        disable(tenantId, kind, id, currentVersion);
        return transition(target, State.PUBLISHED, currentVersion);
    }

    private DefinitionLifecycle transition(
            DefinitionLifecycle current, State state, Long rollback) {
        return lifecycles.save(
                new DefinitionLifecycle(
                        current.tenantId(),
                        current.kind(),
                        current.definitionId(),
                        current.version(),
                        state,
                        current.review(),
                        current.compatibilityImpact(),
                        rollback,
                        clock.instant()));
    }

    private DefinitionLifecycle require(
            TenantId tenantId, DefinitionKind kind, String id, long version) {
        return lifecycles
                .find(tenantId, new DefinitionLifecycleKey(kind, id, version))
                .orElseThrow(() -> new IllegalArgumentException("定义生命周期不存在"));
    }
}
