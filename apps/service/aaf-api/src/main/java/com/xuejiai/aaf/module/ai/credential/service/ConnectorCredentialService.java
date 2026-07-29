package com.xuejiai.aaf.module.ai.credential.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.intelligent.agent.port.CredentialVaultPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.CredentialVaultPort.CredentialHandleMetadata;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.module.ai.credential.vo.ConnectorCredentialRegisterDTO;
import com.xuejiai.aaf.module.ai.credential.vo.ConnectorCredentialVO;

import lombok.RequiredArgsConstructor;

/** Connector 凭证句柄管理；只登记 Vault 引用元数据。 */
@Service
@RequiredArgsConstructor
public class ConnectorCredentialService {

    private final CredentialVaultPort credentials;

    public ConnectorCredentialVO register(
            TenantId tenantId, UserId userId, ConnectorCredentialRegisterDTO request) {
        var now = Instant.now();
        var metadata =
                new CredentialHandleMetadata(
                        "credential:" + UUID.randomUUID().toString().replace("-", ""),
                        tenantId,
                        userId,
                        request.connectorId(),
                        request.scopes(),
                        request.expiresAt(),
                        null,
                        request.vaultRef(),
                        now);
        return ConnectorCredentialVO.from(credentials.register(metadata));
    }

    public void revoke(TenantId tenantId, UserId userId, String handleId) {
        if (!credentials.revoke(tenantId, userId, handleId, Instant.now())) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "凭证句柄不存在或已撤销");
        }
    }
}
