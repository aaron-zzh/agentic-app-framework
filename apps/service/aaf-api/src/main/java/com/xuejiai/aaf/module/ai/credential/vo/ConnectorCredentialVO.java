package com.xuejiai.aaf.module.ai.credential.vo;

import java.time.Instant;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.agent.port.CredentialVaultPort.CredentialHandleMetadata;

public record ConnectorCredentialVO(
        String handleId,
        String connectorId,
        Set<String> scopes,
        Instant expiresAt,
        Instant createdAt) {

    public static ConnectorCredentialVO from(CredentialHandleMetadata metadata) {
        return new ConnectorCredentialVO(
                metadata.handleId(),
                metadata.connectorId(),
                metadata.scopes(),
                metadata.expiresAt(),
                metadata.createdAt());
    }
}
