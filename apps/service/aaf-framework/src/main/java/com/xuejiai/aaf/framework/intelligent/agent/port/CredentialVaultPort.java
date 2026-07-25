package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 服务端 OAuth/Vault 句柄元数据边界；只保存 vaultRef，不保存凭证明文。 */
public interface CredentialVaultPort {

    CredentialHandleMetadata register(CredentialHandleMetadata metadata);

    Optional<CredentialHandleMetadata> findActive(
            TenantId tenantId,
            UserId userId,
            String connectorId,
            Set<String> requiredScopes,
            Instant at);

    Optional<CredentialHandleMetadata> findActiveHandle(
            TenantId tenantId,
            UserId userId,
            String handleId,
            String connectorId,
            Set<String> requiredScopes,
            Instant at);

    boolean revoke(TenantId tenantId, UserId userId, String handleId, Instant at);

    record CredentialHandleMetadata(
            String handleId,
            TenantId tenantId,
            UserId userId,
            String connectorId,
            Set<String> scopes,
            Instant expiresAt,
            Instant revokedAt,
            String vaultRef,
            Instant createdAt) {
        public CredentialHandleMetadata {
            handleId = requireText(handleId, "handleId");
            Objects.requireNonNull(tenantId, "tenantId 不能为空");
            Objects.requireNonNull(userId, "userId 不能为空");
            connectorId = requireText(connectorId, "connectorId");
            scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
            Objects.requireNonNull(expiresAt, "expiresAt 不能为空");
            vaultRef = requireText(vaultRef, "vaultRef");
            Objects.requireNonNull(createdAt, "createdAt 不能为空");
            if (!expiresAt.isAfter(createdAt)) {
                throw new IllegalArgumentException("凭证句柄过期时间必须晚于创建时间");
            }
        }

        public boolean permits(Set<String> requiredScopes, Instant at) {
            return revokedAt == null
                    && at.isBefore(expiresAt)
                    && scopes.containsAll(requiredScopes == null ? Set.of() : requiredScopes);
        }

        private static String requireText(String value, String name) {
            Objects.requireNonNull(value, name + " 不能为空");
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " 不能为空白");
            }
            return value.trim();
        }
    }
}
