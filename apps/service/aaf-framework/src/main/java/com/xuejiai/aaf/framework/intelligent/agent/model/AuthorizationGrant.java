package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** 用户或管理员对单个任务授予的细粒度业务动作权限。 */
public record AuthorizationGrant(
        String grantId,
        TenantId tenantId,
        TaskId taskId,
        String action,
        String resource,
        String scope,
        Instant expiresAt,
        Map<String, String> conditions,
        boolean reversible,
        String credentialHandle,
        String grantedBy,
        Instant grantedAt,
        Instant revokedAt) {

    public AuthorizationGrant {
        grantId = requireText(grantId, "grantId");
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(taskId, "taskId 不能为空");
        action = requireText(action, "action");
        resource = requireText(resource, "resource");
        if ("*".equals(resource)) {
            throw new IllegalArgumentException("普通任务授权禁止通配 resource");
        }
        scope = requireText(scope, "scope");
        Objects.requireNonNull(expiresAt, "expiresAt 不能为空");
        conditions = conditions == null ? Map.of() : Map.copyOf(conditions);
        if (credentialHandle != null && credentialHandle.isBlank()) {
            throw new IllegalArgumentException("credentialHandle 不能为空白");
        }
        grantedBy = requireText(grantedBy, "grantedBy");
        Objects.requireNonNull(grantedAt, "grantedAt 不能为空");
        if (!expiresAt.isAfter(grantedAt)) {
            throw new IllegalArgumentException("授权过期时间必须晚于授予时间");
        }
    }

    public boolean activeAt(Instant at) {
        return revokedAt == null && at.isBefore(expiresAt);
    }

    public boolean matches(
            String requestedAction,
            String requestedResource,
            String requestedScope,
            Map<String, String> requestedConditions,
            boolean requestedReversible) {
        return action.equals(requestedAction)
                && resource.equals(requestedResource)
                && scope.equals(requestedScope)
                && conditions.equals(requestedConditions == null ? Map.of() : requestedConditions)
                && (!requestedReversible || reversible);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空白");
        }
        return value.trim();
    }
}
