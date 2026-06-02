package com.xuejiai.aaf.framework.security.access;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;

/** 默认统一权限决策服务，聚合 L1/L2/L3/L4 的 SPI 实现。 */
@Service
@RequiredArgsConstructor
public class DefaultAccessDecisionService implements AccessDecisionService {

    private final OperatorContext operatorContext;
    private final ObjectProvider<FunctionPermissionChecker> functionPermissionChecker;
    private final ObjectProvider<RelationPermissionChecker> relationPermissionChecker;
    private final ObjectProvider<RecordRuleSupport> recordRuleSupport;
    private final PolicyEngine policyEngine;

    @Override
    public boolean hasPermission(String permissionCode) {
        if (hasSuperAdminAuthority()) {
            return true;
        }
        var userId = operatorContext.currentOwnerId().orElse(null);
        var checker = functionPermissionChecker.getIfAvailable();
        return userId != null && checker != null && checker.hasPermission(userId, permissionCode);
    }

    @Override
    public boolean hasPermission(String objectType, String objectId, String relationPermission) {
        if (hasSuperAdminAuthority()) {
            return true;
        }
        var userId = operatorContext.currentOwnerId().orElse(null);
        var checker = relationPermissionChecker.getIfAvailable();
        return userId != null
                && checker != null
                && checker.hasPermission(userId, objectType, objectId, relationPermission);
    }

    @Override
    public <T> Specification<T> recordRuleSpec(String entitySlug) {
        if (hasSuperAdminAuthority()) {
            return null;
        }
        var userId = operatorContext.currentOwnerId().orElse(null);
        var support = recordRuleSupport.getIfAvailable();
        return userId == null || support == null
                ? null
                : support.buildAccessSpec(entitySlug, userId);
    }

    @Override
    public PolicyResult evaluatePolicy(PolicyInput input) {
        return policyEngine.evaluate(input);
    }

    private boolean hasSuperAdminAuthority() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch("ROLE_SUPER_ADMIN"::equals);
    }
}
