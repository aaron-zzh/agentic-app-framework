package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizationGrant;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** 任务授权持久化边界；所有操作必须携带 tenant。 */
public interface AuthorizationGrantPort {

    AuthorizationGrant grant(AuthorizationGrant grant);

    Optional<AuthorizationGrant> findActive(
            TenantId tenantId,
            TaskId taskId,
            String action,
            String resource,
            String scope,
            Map<String, String> conditions,
            boolean reversible,
            Instant at);

    List<AuthorizationGrant> list(TenantId tenantId, TaskId taskId);

    boolean revoke(TenantId tenantId, String grantId, String revokedBy, Instant at);
}
