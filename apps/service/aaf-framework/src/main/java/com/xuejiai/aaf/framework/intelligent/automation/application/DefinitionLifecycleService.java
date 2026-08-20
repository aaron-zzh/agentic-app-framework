package com.xuejiai.aaf.framework.intelligent.automation.application;

import java.time.Clock;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationGovernance.*;
import com.xuejiai.aaf.framework.intelligent.automation.port.AutomationPorts.LifecyclePort;
import com.xuejiai.aaf.framework.intelligent.automation.port.AutomationPorts.LifecyclePort.DefinitionLifecycleKey;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.team.model.TeamDefinition;

/** Assistant、Skill、Automation、Connector、Team 的统一生命周期治理。 */
public final class DefinitionLifecycleService {
    private final LifecyclePort lifecycles;
    private final Clock clock;
    private final AssistantDefinitionPort assistantDefinitions;

    public DefinitionLifecycleService(
            LifecyclePort lifecycles, Clock clock, AssistantDefinitionPort assistantDefinitions) {
        this.lifecycles = lifecycles;
        this.clock = clock;
        this.assistantDefinitions = assistantDefinitions;
    }

    public DefinitionLifecycle draft(
            TenantId tenantId,
            DefinitionKind kind,
            String id,
            long version,
            CompatibilityImpact impact,
            TeamDefinition teamDefinition) {
        if (kind == DefinitionKind.TEAM) {
            validateTeamDefinition(tenantId, teamDefinition);
            lifecycles
                    .find(tenantId, new DefinitionLifecycleKey(kind, id, version))
                    .filter(existing -> existing.state() != State.DRAFT)
                    .ifPresent(
                            existing -> {
                                throw new IllegalStateException("已发布的 Team 版本不可覆盖");
                            });
        }
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
                        teamDefinition,
                        clock.instant()));
    }

    public TeamDefinition requirePublishedTeam(TenantId tenantId, String id, long version) {
        var lifecycle = require(tenantId, DefinitionKind.TEAM, id, version);
        if (lifecycle.state() != State.PUBLISHED) {
            throw new IllegalStateException("Team 定义版本不是 PUBLISHED");
        }
        return lifecycle.teamDefinition();
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
                        current.teamDefinition(),
                        clock.instant()));
    }

    public DefinitionLifecycle publish(
            TenantId tenantId, DefinitionKind kind, String id, long version) {
        var current = require(tenantId, kind, id, version);
        current.review().requireApproved();
        if (current.kind() == DefinitionKind.TEAM) {
            validateTeamDefinition(tenantId, current.teamDefinition());
        }
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
        if (target.kind() == DefinitionKind.TEAM) {
            validateTeamDefinition(tenantId, target.teamDefinition());
        }
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
                        current.teamDefinition(),
                        clock.instant()));
    }

    private void validateTeamDefinition(TenantId tenantId, TeamDefinition teamDefinition) {
        if (teamDefinition == null) {
            throw new IllegalArgumentException("TEAM lifecycle 必须携带 teamDefinition");
        }
        validateTeamMember(tenantId, teamDefinition.leader());
        teamDefinition.workers().forEach(member -> validateTeamMember(tenantId, member));
    }

    private void validateTeamMember(TenantId tenantId, TeamDefinition.Member member) {
        AssistantDefinition definition =
                assistantDefinitions
                        .findById(tenantId, new AssistantId(member.assistantId()))
                        .orElseThrow(() -> new IllegalArgumentException("Assistant 定义不存在"));
        if (definition.lifecycle() != AssistantDefinition.Lifecycle.PUBLISHED) {
            throw new IllegalStateException("Team 成员 Assistant 必须已发布: " + member.memberKey());
        }
        if (definition.version().value() != member.assistantRevision()) {
            throw new IllegalStateException(
                    "Team 成员 Assistant revision 不匹配: " + member.memberKey());
        }
        var role = definition.requireRole(member.roleKey());
        if (!role.skillKeys().contains(member.skillKey())) {
            throw new IllegalStateException("Team 成员固定 Skill 不存在: " + member.memberKey());
        }
        if (!definition.toolPolicy().rules().keySet().containsAll(member.allowedToolKeys())) {
            throw new IllegalStateException("Team 成员工具权限超出 Assistant 白名单: " + member.memberKey());
        }
        if (!role.toolKeys().containsAll(member.allowedToolKeys())) {
            throw new IllegalStateException("Team 成员工具权限超出 Role 白名单: " + member.memberKey());
        }
    }

    private DefinitionLifecycle require(
            TenantId tenantId, DefinitionKind kind, String id, long version) {
        return lifecycles
                .find(tenantId, new DefinitionLifecycleKey(kind, id, version))
                .orElseThrow(() -> new IllegalArgumentException("定义生命周期不存在"));
    }
}
