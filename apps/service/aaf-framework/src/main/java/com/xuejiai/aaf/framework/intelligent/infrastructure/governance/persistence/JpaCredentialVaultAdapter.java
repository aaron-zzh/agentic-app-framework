package com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.agent.port.CredentialVaultPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 只持久化 opaque handle 与 vaultRef 元数据的生产 adapter。 */
public final class JpaCredentialVaultAdapter implements CredentialVaultPort {

    private final CredentialHandleRepository repository;

    public JpaCredentialVaultAdapter(CredentialHandleRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
    }

    @Override
    public CredentialHandleMetadata register(CredentialHandleMetadata metadata) {
        var existing = repository.findById(metadata.handleId()).map(this::toDomain);
        if (existing.isPresent()) {
            return requireSame(existing.get(), metadata);
        }
        try {
            return toDomain(repository.saveAndFlush(toEntity(metadata)));
        } catch (DataIntegrityViolationException conflict) {
            var concurrent = repository.findById(metadata.handleId())
                    .map(this::toDomain)
                    .orElseThrow(() -> conflict);
            return requireSame(concurrent, metadata);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CredentialHandleMetadata> findActive(
            TenantId tenantId,
            UserId userId,
            String connectorId,
            Set<String> requiredScopes,
            Instant at) {
        return repository.findActive(tenantId.value(), userId.value(), connectorId, at).stream()
                .map(this::toDomain)
                .filter(handle -> handle.permits(requiredScopes, at))
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CredentialHandleMetadata> findActiveHandle(
            TenantId tenantId,
            UserId userId,
            String handleId,
            String connectorId,
            Set<String> requiredScopes,
            Instant at) {
        return repository.findById(handleId)
                .map(this::toDomain)
                .filter(handle -> handle.tenantId().equals(tenantId))
                .filter(handle -> handle.userId().equals(userId))
                .filter(handle -> handle.connectorId().equals(connectorId))
                .filter(handle -> handle.permits(requiredScopes, at));
    }

    @Override
    @Transactional
    public boolean revoke(TenantId tenantId, UserId userId, String handleId, Instant at) {
        return repository.revoke(tenantId.value(), userId.value(), handleId, at) == 1;
    }

    private CredentialHandleEntity toEntity(CredentialHandleMetadata metadata) {
        var entity = new CredentialHandleEntity();
        entity.setHandleId(metadata.handleId());
        entity.setTenantId(metadata.tenantId().value());
        entity.setUserId(metadata.userId().value());
        entity.setConnectorId(metadata.connectorId());
        entity.setScopes(metadata.scopes().stream().sorted().toList());
        entity.setExpiresAt(metadata.expiresAt());
        entity.setRevokedAt(metadata.revokedAt());
        entity.setVaultRef(metadata.vaultRef());
        entity.setCreatedAt(metadata.createdAt());
        return entity;
    }

    private CredentialHandleMetadata toDomain(CredentialHandleEntity entity) {
        return new CredentialHandleMetadata(
                entity.getHandleId(),
                new TenantId(entity.getTenantId()),
                new UserId(entity.getUserId()),
                entity.getConnectorId(),
                Set.copyOf(entity.getScopes()),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getVaultRef(),
                entity.getCreatedAt());
    }

    private static CredentialHandleMetadata requireSame(
            CredentialHandleMetadata existing, CredentialHandleMetadata requested) {
        if (!existing.equals(requested)) {
            throw new IllegalStateException("handleId 已绑定不同 Vault 元数据");
        }
        return existing;
    }
}
