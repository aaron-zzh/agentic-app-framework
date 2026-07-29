package com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizationGrant;
import com.xuejiai.aaf.framework.intelligent.agent.port.AuthorizationGrantPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

public final class JpaAuthorizationGrantAdapter implements AuthorizationGrantPort {
    private final AuthorizationGrantRepository repository;

    public JpaAuthorizationGrantAdapter(AuthorizationGrantRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
    }

    @Override
    public AuthorizationGrant grant(AuthorizationGrant grant) {
        var existing = repository.findById(grant.grantId()).map(this::toDomain);
        if (existing.isPresent()) {
            return requireSameGrant(existing.get(), grant);
        }
        var entity = new AuthorizationGrantEntity();
        entity.setGrantId(grant.grantId());
        entity.setTenantId(grant.tenantId().value());
        entity.setTaskId(grant.taskId().value());
        entity.setAction(grant.action());
        entity.setResource(grant.resource());
        entity.setExpiresAt(grant.expiresAt());
        entity.setRevokedAt(grant.revokedAt());
        entity.setGrant(grant);
        try {
            return toDomain(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException conflict) {
            var concurrent =
                    repository
                            .findById(grant.grantId())
                            .map(this::toDomain)
                            .orElseThrow(() -> conflict);
            return requireSameGrant(concurrent, grant);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthorizationGrant> findActive(
            TenantId tenantId,
            TaskId taskId,
            String action,
            String resource,
            String scope,
            Map<String, String> conditions,
            boolean reversible,
            Instant at) {
        return repository.findActive(tenantId.value(), taskId.value(), action, at).stream()
                .map(this::toDomain)
                .filter(
                        grant ->
                                grant.activeAt(at)
                                        && grant.matches(
                                                action, resource, scope, conditions, reversible))
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuthorizationGrant> list(TenantId tenantId, TaskId taskId) {
        return repository
                .findByTenantIdAndTaskIdOrderByExpiresAtDesc(tenantId.value(), taskId.value())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public boolean revoke(TenantId tenantId, String grantId, String revokedBy, Instant at) {
        Objects.requireNonNull(revokedBy, "revokedBy 不能为空");
        return repository.revoke(tenantId.value(), grantId, at) == 1;
    }

    private static AuthorizationGrant requireSameGrant(
            AuthorizationGrant existing, AuthorizationGrant requested) {
        if (!existing.equals(requested)) {
            throw new IllegalStateException("grantId 已绑定不同授权事实: " + requested.grantId());
        }
        return existing;
    }

    private AuthorizationGrant toDomain(AuthorizationGrantEntity entity) {
        var snapshot = entity.getGrant();
        return new AuthorizationGrant(
                snapshot.grantId(),
                snapshot.tenantId(),
                snapshot.taskId(),
                snapshot.action(),
                snapshot.resource(),
                snapshot.scope(),
                entity.getExpiresAt(),
                snapshot.conditions(),
                snapshot.reversible(),
                snapshot.credentialHandle(),
                snapshot.grantedBy(),
                snapshot.grantedAt(),
                entity.getRevokedAt());
    }
}
